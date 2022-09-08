import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.2.0`

import mill._
import mill.define.Target
import mill.scalalib._
import mill.scalanativelib._
import mill.scalalib.publish._
import mill.scalalib.api.Util.isScala3

import de.tobiasroeser.mill.vcs.version.VcsVersion

val communityBuildDottyVersion = sys.props.get("dottyVersion").toList

val scalaVersions = "3.1.3" :: "2.12.16" :: "2.13.8" :: "2.11.12" :: communityBuildDottyVersion

val scalaNativeVersions = scalaVersions.map((_, "0.4.5"))

object os extends Module {
  object jvm extends Cross[OsJvmModule](scalaVersions:_*)

  class OsJvmModule(val crossScalaVersion: String) extends OsModule {
    def platformSegment = "jvm"
    object test extends Tests with OsLibTestModule{
      def platformSegment = "jvm"
    }
  }
  object native extends Cross[OsNativeModule](scalaNativeVersions:_*)

  class OsNativeModule(val crossScalaVersion: String, crossScalaNativeVersion: String) extends OsModule with ScalaNativeModule {
    def platformSegment = "native"
    def millSourcePath = super.millSourcePath / _root_.os.up
    def scalaNativeVersion = crossScalaNativeVersion
    object test extends Tests with OsLibTestModule{
      def platformSegment = "native"
      def nativeLinkStubs = true
    }
  }

  object watch extends Module {
    object jvm extends Cross[WatchJvmModule](scalaVersions:_*)
    class WatchJvmModule(val crossScalaVersion: String) extends WatchModule {
      def platformSegment = "jvm"
      def moduleDeps = super.moduleDeps :+ os.jvm()
      def ivyDeps = Agg(
        ivy"net.java.dev.jna:jna:5.12.1"
      )
      object test extends Tests with OsLibTestModule {
        def platformSegment = "jvm"
        def moduleDeps = super.moduleDeps :+ os.jvm().test
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

trait OsLibModule extends CrossScalaModule with PublishModule{
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
  def millSourcePath = super.millSourcePath / _root_.os.up
  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )
  def acyclicDep: T[Agg[Dep]] = T { if (!isScala3(crossScalaVersion)) Agg(ivy"com.lihaoyi:::acyclic:0.3.3") else Agg() }
  def compileIvyDeps = acyclicDep
  def scalacOptions = T { if (!isScala3(crossScalaVersion)) Seq("-P:acyclic:force") else Seq.empty }
  def scalacPluginIvyDeps = acyclicDep
}

trait OsLibTestModule extends ScalaModule with TestModule.Utest {
  def ivyDeps = Agg(
    ivy"com.lihaoyi::utest::0.8.0",
    ivy"com.lihaoyi::sourcecode::0.3.0"
  )

  def platformSegment: String
  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )

  // we check the textual output of system commands and expect it in english
  override def forkEnv: Target[Map[String, String]] = super.forkEnv() ++ Map("LC_ALL" -> "C")
}
trait OsModule extends OsLibModule{
  def artifactName = "os-lib"

  def ivyDeps = Agg(
    ivy"com.lihaoyi::geny::0.7.1"
  )
}

trait WatchModule extends OsLibModule{
  def artifactName = "os-lib-watch"
}
