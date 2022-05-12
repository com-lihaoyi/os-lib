import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.1.4`

import mill._
import mill.define.Target
import mill.scalalib._
import mill.scalanativelib._
import mill.scalalib.publish._

import de.tobiasroeser.mill.vcs.version.VcsVersion

val dottyVersions = sys.props.get("dottyVersion").toList

val scala2AndDottyVersions = "2.12.13" :: "2.13.4" :: "2.11.12" :: dottyVersions
val scala30 = "3.0.2"
val scala31 = "3.1.1"

val scalaNativeVersions = for {
  scalaV <- scala31 :: scala2AndDottyVersions
  scalaNativeV <- Seq("0.4.3")
} yield (scalaV, scalaNativeV)

object os extends Module {
  object jvm extends Cross[OsJvmModule](scala30 :: scala2AndDottyVersions:_*)

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
    object jvm extends Cross[WatchJvmModule](scala30 :: scala2AndDottyVersions:_*)
    class WatchJvmModule(val crossScalaVersion: String) extends WatchModule {
      def platformSegment = "jvm"
      def moduleDeps = super.moduleDeps :+ os.jvm()
      def ivyDeps = Agg(
        ivy"net.java.dev.jna:jna:5.0.0",
        ivy"com.lihaoyi::sourcecode::0.2.5",
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
  def isDotty = crossScalaVersion.startsWith("0") || crossScalaVersion.startsWith("3")
  def publishVersion = VcsVersion.vcsState().format()
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "com.lihaoyi",
    url = "https://github.com/lihaoyi/os",
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
  def acyclicDep: T[Agg[Dep]] = T { if (!isDotty) Agg(ivy"com.lihaoyi:::acyclic:0.3.2") else Agg() }
  def compileIvyDeps = acyclicDep
  def scalacOptions = T { if (!isDotty) Seq("-P:acyclic:force") else Seq.empty }
  def scalacPluginIvyDeps = acyclicDep
}

trait OsLibTestModule extends ScalaModule with TestModule{
  def ivyDeps = Agg(
    ivy"com.lihaoyi::utest::0.7.11",
    ivy"com.lihaoyi::sourcecode::0.2.8"
    if (scalaVersion().startsWith("2.11"))
       ivy"org.scalacheck::scalacheck::1.15.2"
    else
       ivy"org.scalacheck::scalacheck::1.15.3"
  )

  def platformSegment: String
  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )

  def testFrameworks = Seq("utest.runner.Framework")
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
