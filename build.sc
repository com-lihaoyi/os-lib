import mill._, scalalib._, scalanativelib._, publish._

val crossScalaVersions = Seq("2.12.10", "2.13.1", "3.0.0-M2")
val crossNativeVersions = Seq(
  "2.11.12" -> "0.3.9",
  "2.11.12" -> "0.4.0-M2"
)
def acyclicVersion(scalaVersion: String): String = if(scalaVersion.startsWith("2.11.")) "0.1.8" else "0.2.0"

object os extends Module {
  object jvm extends Cross[OsJvmModule](crossScalaVersions:_*)

  class OsJvmModule(val crossScalaVersion: String) extends OsModule {
    def platformSegment = "jvm"
    object test extends Tests with OsLibTestModule{
      def platformSegment = "jvm"
    }
  }
  object native extends Cross[OsNativeModule](crossNativeVersions:_*)

  class OsNativeModule(val crossScalaVersion: String, crossScalaNativeVersion: String) extends OsModule with ScalaNativeModule {
    def platformSegment = "native"
    def millSourcePath = super.millSourcePath / ammonite.ops.up
    def scalaNativeVersion = crossScalaNativeVersion
    object test extends Tests with OsLibTestModule{
      def sources = if(scalaNativeVersion == "0.3.9") T.sources() else super.sources
      def platformSegment = "native"
      def nativeLinkStubs = true
    }
  }

  object watch extends Module {
    object jvm extends Cross[WatchJvmModule](crossScalaVersions:_*)
    class WatchJvmModule(val crossScalaVersion: String) extends WatchModule {
      def platformSegment = "jvm"
      def moduleDeps = super.moduleDeps :+ os.jvm()
      def ivyDeps = Agg(
        ivy"net.java.dev.jna:jna:5.0.0"
      )
      object test extends Tests with OsLibTestModule {
        def platformSegment = "jvm"
        def moduleDeps = super.moduleDeps :+ os.jvm().test
      }
    }

    /*
    object native extends Cross[WatchNativeModule](crossNativeVersions:_*)
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
  def publishVersion = "0.7.1"
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "com.lihaoyi",
    url = "https://github.com/lihaoyi/os",
    licenses = Seq(License.MIT),
    scm = SCM(
      "git://github.com/lihaoyi/os.git",
      "scm:git://github.com/lihaoyi/os.git"
    ),
    developers = Seq(
      Developer("lihaoyi", "Li Haoyi","https://github.com/lihaoyi")
    )
  )

  def platformSegment: String
  def millSourcePath = super.millSourcePath / ammonite.ops.up
  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )
  def acyclicDep: T[Agg[Dep]] = T { if (!isDotty) Agg(ivy"com.lihaoyi::acyclic:${acyclicVersion(scalaVersion())}") else Agg() }
  def compileIvyDeps = acyclicDep
  def scalacOptions = T { if (!isDotty) Seq("-P:acyclic:force") else Seq.empty }
  def scalacPluginIvyDeps = acyclicDep

}

trait OsLibTestModule extends ScalaModule with TestModule{
  def ivyDeps = Agg(
    ivy"com.lihaoyi::utest::0.7.5",
    ivy"com.lihaoyi::sourcecode::0.2.1"
  )

  def platformSegment: String
  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )

  def testFrameworks = Seq("utest.runner.Framework")
}
trait OsModule extends OsLibModule{
  def artifactName = "os-lib"

  def ivyDeps = Agg(
    ivy"com.lihaoyi::geny::0.6.2"
  )
}

trait WatchModule extends OsLibModule{
  def artifactName = "os-lib-watch"
}
