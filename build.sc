// plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.3.1-6-e80da7`
import $ivy.`com.github.lolgab::mill-mima::0.0.20`

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

object Deps {
  val acyclic = ivy"com.lihaoyi:::acyclic:0.3.6"
  val jna = ivy"net.java.dev.jna:jna:5.13.0"
  val geny = ivy"com.lihaoyi::geny::1.0.0"
  val sourcecode = ivy"com.lihaoyi::sourcecode::0.3.0"
  val utest = ivy"com.lihaoyi::utest::0.8.1"
  def scalaLibrary(version: String) = ivy"org.scala-lang:scala-library:${version}"
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
  def compileIvyDeps = acyclicDep
  def scalacPluginIvyDeps = acyclicDep
  def scalacOptions = super.scalacOptions() ++ acyclicOptions()
}

trait SafeDeps extends ScalaModule {
  def mapDependencies: Task[coursier.Dependency => coursier.Dependency] = T.task {
    val sd = Deps.scalaLibrary(scala213Version)
    super.mapDependencies().andThen { d =>
      // enforce up-to-date Scala 2.13.x version
      if (d.module == sd.dep.module && d.version.startsWith("2.13.")) sd.dep
      else d
    }
  }
}

trait MiMaChecks extends Mima {
  def mimaPreviousVersions = Seq("0.9.0", "0.9.1")
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
}

trait OsModule extends OsLibModule with PlatformScalaModule{
  def ivyDeps = Agg(Deps.geny)

  // Properly identify the last non-cross segment to treat as platform
  // TODO: remove this once Mill supports this built-in
  def sources = T.sources {
    val platform = millModuleSegments
      .value
      .collect { case l: mill.define.Segment.Label => l.value }
      .last

    super.sources().flatMap { source =>
      val platformPath = PathRef(source.path / _root_.os.up / s"${source.path.last}-${platform}")
      Seq(source, platformPath)
    }
  }
}

trait OsLibTestModule extends ScalaModule with TestModule.Utest with SafeDeps {
  def ivyDeps = Agg(Deps.utest, Deps.sourcecode)

  // we check the textual output of system commands and expect it in english
  def forkEnv: Target[Map[String, String]] = T {
    super.forkEnv() ++ Map("LC_ALL" -> "C")
  }
}

object os extends Module {

  object jvm extends Cross[OsJvmModule](scalaVersions)
  trait OsJvmModule extends OsModule with MiMaChecks {
    object test extends Tests with OsLibTestModule
  }

  object native extends Cross[OsNativeModule](scalaVersions)
  trait OsNativeModule extends OsModule with ScalaNativeModule{
    def scalaNativeVersion = "0.4.5"
    object test extends Tests with OsLibTestModule {
      def nativeLinkStubs = true
    }
  }

  object watch extends Module {
    object jvm extends Cross[WatchJvmModule](scalaVersions)
    trait WatchJvmModule extends OsLibModule {
      def moduleDeps = super.moduleDeps ++ Seq(os.jvm())
      def ivyDeps = Agg(Deps.jna)
      object test extends Tests with OsLibTestModule {
        def moduleDeps = super.moduleDeps ++ Seq(os.jvm().test)
      }
    }
  }
}


