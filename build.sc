import mill._, scalalib._, publish._

trait OsLibModule extends CrossScalaModule with PublishModule{
  def publishVersion = "0.6.2"
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

  def compileIvyDeps = Agg(ivy"com.lihaoyi::acyclic:0.2.0")
  def scalacOptions = Seq("-P:acyclic:force")
  def scalacPluginIvyDeps = Agg(ivy"com.lihaoyi::acyclic:0.2.0")
  trait OsLibTestModule extends Tests{
    def ivyDeps = Agg(
      ivy"com.lihaoyi::utest::0.7.1",
      ivy"com.lihaoyi::sourcecode::0.1.7"
    )

    def testFrameworks = Seq("utest.runner.Framework")
  }
}
object os extends Cross[OsModule]("2.12.7", "2.13.0"){
  object watch extends Cross[WatchModule]("2.12.7", "2.13.0")
  class WatchModule(val crossScalaVersion: String) extends OsLibModule{
    def artifactName = "os-lib-watch"
    def moduleDeps = Seq(os())
    def ivyDeps = Agg(
      ivy"net.java.dev.jna:jna:5.0.0"
    )


    object test extends OsLibTestModule {
      def moduleDeps = super.moduleDeps ++ Seq(os().test)
    }
  }

}
class OsModule(val crossScalaVersion: String) extends OsLibModule{
  def artifactName = "os-lib"

  def ivyDeps = Agg(
    ivy"com.lihaoyi::geny:0.4.2"
  )

  object test extends OsLibTestModule
}
