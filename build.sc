import mill._, scalalib._, publish._

object os extends Cross[OsModule]("2.11.12", "2.12.7")
class OsModule(val crossScalaVersion: String) extends CrossScalaModule with PublishModule{
  def artifactName = "os-lib"
  def publishVersion = "0.2.2"
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

  def compileIvyDeps = Agg(ivy"com.lihaoyi::acyclic:0.1.7")
  def scalacOptions = Seq("-P:acyclic:force")
  def scalacPluginIvyDeps = Agg(ivy"com.lihaoyi::acyclic:0.1.7")

  def ivyDeps = Agg(ivy"com.lihaoyi::geny:0.1.5")

  object test extends Tests {
    def ivyDeps = Agg(
      ivy"com.lihaoyi::utest::0.6.5",
      ivy"com.lihaoyi::sourcecode::0.1.5",
      ivy"com.lihaoyi::pprint::0.5.3"
    )

    def testFrameworks = Seq("utest.runner.Framework")
  }
}
