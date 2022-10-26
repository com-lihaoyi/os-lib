// plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.3.0`
import $ivy.`com.github.lolgab::mill-mima::0.0.13`

// imports
import mill._
import mill.define.{Task, Target}
import mill.scalalib._
import mill.scalanativelib._
import mill.scalalib.publish._
import mill.scalalib.api.ZincWorkerUtil
// avoid name collisions
import _root_.{os => oslib}
import com.github.lolgab.mill.mima.Mima

import de.tobiasroeser.mill.vcs.version.VcsVersion

val communityBuildDottyVersion = sys.props.get("dottyVersion").toList

val scala213Version = "2.13.10"

val scalaVersions = Seq(
  "3.1.3",
  "2.12.17",
  scala213Version,
  "2.11.12"
) ++ communityBuildDottyVersion

val scalaNativeVersions = scalaVersions.map((_, "0.4.5"))

val backwardCompatibleVersions: Seq[String] = Seq()

object Deps {
  val acyclic = ivy"com.lihaoyi:::acyclic:0.3.6"
  val jna = ivy"net.java.dev.jna:jna:5.12.1"
  val geny = ivy"com.lihaoyi::geny::1.0.0"
  val sourcecode = ivy"com.lihaoyi::sourcecode::0.3.0"
  val utest = ivy"com.lihaoyi::utest::0.8.1"
  def scalaLibrary(version: String) = ivy"org.scala-lang:scala-library:${version}"
}

object os extends Module {

  object jvm extends Cross[OsJvmModule](scalaVersions: _*)
  class OsJvmModule(val crossScalaVersion: String) extends OsModule with MiMaChecks {
    def platformSegment = "jvm"
    object test extends Tests with OsLibTestModule {
      def platformSegment = "jvm"
    }
  }

  object native extends Cross[OsNativeModule](scalaNativeVersions: _*)
  class OsNativeModule(
      val crossScalaVersion: String,
      crossScalaNativeVersion: String
  ) extends OsModule
      with ScalaNativeModule {
    def platformSegment = "native"
    override def millSourcePath = super.millSourcePath / oslib.up
    def scalaNativeVersion = crossScalaNativeVersion
    object test extends Tests with OsLibTestModule {
      def platformSegment = "native"
      override def nativeLinkStubs = true
    }
  }

  object watch extends Module {

    object jvm extends Cross[WatchJvmModule](scalaVersions: _*)
    class WatchJvmModule(val crossScalaVersion: String) extends WatchModule {
      def platformSegment = "jvm"
      override def moduleDeps = super.moduleDeps :+ os.jvm()
      override def ivyDeps = Agg(
        Deps.jna
      )
      object test extends Tests with OsLibTestModule {
        def platformSegment = "jvm"
        override def moduleDeps = super.moduleDeps :+ os.jvm().test
      }
    }

    /*
    object native extends Cross[WatchNativeModule](scalaNativeVersions:_*)
    class WatchNativeModule(val crossScalaVersion: String, crossScalaNativeVersion: String) extends WatchModule with ScalaNativeModule {
      def platformSegment = "native"
      def millSourcePath = super.millSourcePath / ammonite.ops.up
      def scalaNativeVersion = crossScalaNativeVersion
      def moduleDeps = super.moduleDeps :+ os.native()
      object test extends Tests with OsLibTestModule {
        def platformSegment = "native"
        def moduleDeps = super.moduleDeps :+ os.native().test
        def nativeLinkStubs = true
      }
    }
     */
  }
}

trait AcyclicModule extends ScalaModule {
  def acyclicDep: T[Agg[Dep]] = T {
    if (!ZincWorkerUtil.isScala3(scalaVersion())) Agg(Deps.acyclic)
    else Agg.empty[Dep]
  }
  def acyclicOptions: T[Seq[String]] = T {
    if (!ZincWorkerUtil.isScala3(scalaVersion())) Seq("-P:acyclic:force")
    else Seq.empty
  }
  override def compileIvyDeps = acyclicDep
  override def scalacPluginIvyDeps = acyclicDep
  override def scalacOptions = T {
    super.scalacOptions() ++ acyclicOptions()
  }
}

trait SafeDeps extends ScalaModule {
  override def mapDependencies: Task[coursier.Dependency => coursier.Dependency] = T.task {
    val sd = Deps.scalaLibrary(scala213Version)
    super.mapDependencies().andThen { d =>
      // enforce up-to-date Scala 3.13 version
      if (d.module == sd.dep.module && d.version.startsWith("2.13.")) {
        sd.dep
      } else d
    }
  }
}

trait OsLibModule extends CrossScalaModule with PublishModule with AcyclicModule with SafeDeps {
  def publishVersion = VcsVersion.vcsState().format()
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "com.lihaoyi",
    url = "https://github.com/com-lihaoyi/os-lib",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github(
      owner = "com-lihaoyi",
      repo = "os-lib"
    ),
    developers = Seq(
      Developer("lihaoyi", "Li Haoyi", "https://github.com/lihaoyi")
    )
  )
  def platformSegment: String
  override def millSourcePath = super.millSourcePath / oslib.up
  override def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )
}

trait OsLibTestModule extends ScalaModule with TestModule.Utest with SafeDeps {
  override def ivyDeps = Agg(
    Deps.utest,
    Deps.sourcecode
  )
  def platformSegment: String
  override def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )

  // we check the textual output of system commands and expect it in english
  override def forkEnv: Target[Map[String, String]] = T {
    super.forkEnv() ++ Map("LC_ALL" -> "C")
  }
}

trait OsModule extends OsLibModule {
  override def artifactName = "os-lib"
  override def ivyDeps = Agg(
    Deps.geny
  )
}

trait WatchModule extends OsLibModule {
  override def artifactName = "os-lib-watch"
}

trait MiMaChecks extends Mima {
  override def mimaPreviousVersions = backwardCompatibleVersions
  override def mimaPreviousArtifacts: Target[Agg[Dep]] = T {
    val versions = mimaPreviousVersions().distinct
    val info = artifactMetadata()
    if (versions.isEmpty)
      T.log.error("No binary compatible versions configured!")
    Agg.from(
      versions.map(version =>
        ivy"${info.group}:${info.id}:${version}"
      )
    )
  }
}
