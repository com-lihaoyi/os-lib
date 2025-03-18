// plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`
import $ivy.`com.github.lolgab::mill-mima::0.1.0`

// imports
import mill._, scalalib._, scalanativelib._, publish._
import mill.scalalib.api.ZincWorkerUtil
import com.github.lolgab.mill.mima._
import de.tobiasroeser.mill.vcs.version.VcsVersion

val communityBuildDottyVersion = sys.props.get("dottyVersion").toList

val scala213Version = "2.13.10"

val scalaVersions = Seq(
  "3.3.1",
  "2.12.17",
  scala213Version,
) ++ communityBuildDottyVersion

object Deps {
  val acyclic = ivy"com.lihaoyi:::acyclic:0.3.9"
  val jna = ivy"net.java.dev.jna:jna:5.13.0"
  val geny = ivy"com.lihaoyi::geny::1.0.0"
  val sourcecode = ivy"com.lihaoyi::sourcecode::0.3.1"
  val utest = ivy"com.lihaoyi::utest::0.8.2"
  def scalaLibrary(version: String) = ivy"org.scala-lang:scala-library:${version}"
}

trait AcyclicModule extends ScalaModule {
  def acyclicDep: T[Agg[Dep]] = T {
    Agg.from(Option.when(!ZincWorkerUtil.isScala3(scalaVersion()))(Deps.acyclic))
  }
  def acyclicOptions: T[Seq[String]] = T {
    Option.when(!ZincWorkerUtil.isScala3(scalaVersion()))("-P:acyclic:force").toSeq
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
  def mimaPreviousVersions = Seq("0.9.0", "0.9.1", "0.9.2")
  override def mimaBinaryIssueFilters: T[Seq[ProblemFilter]] = Seq(
    ProblemFilter.exclude[ReversedMissingMethodProblem]("os.PathConvertible.isCustomFs")
  )
}

object testJarWriter extends JavaModule
object testJarReader extends JavaModule
object testJarExit extends JavaModule

trait OsLibModule
    extends CrossScalaModule
    with PublishModule
    with AcyclicModule
    with SafeDeps
    with PlatformScalaModule { outer =>

  def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(ivy"com.lihaoyi::unroll-plugin:0.1.12")
  def ivyDeps = super.ivyDeps() ++ Agg(ivy"com.lihaoyi::unroll-annotation:0.1.12")
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

  trait OsLibTestModule extends ScalaModule with TestModule.Utest with SafeDeps {
    def ivyDeps = Agg(Deps.utest, Deps.sourcecode)

    // we check the textual output of system commands and expect it in english
    def forkEnv = super.forkEnv() ++ Map(
      "LC_ALL" -> "C",
      "TEST_JAR_WRITER_ASSEMBLY" -> testJarWriter.assembly().path.toString,
      "TEST_JAR_READER_ASSEMBLY" -> testJarReader.assembly().path.toString,
      "TEST_JAR_EXIT_ASSEMBLY" -> testJarExit.assembly().path.toString,
      "TEST_SUBPROCESS_ENV" -> "value"
    )
  }
}

trait OsModule extends OsLibModule { outer =>
  def ivyDeps = super.ivyDeps() ++ Agg(Deps.geny)

  def artifactName = "os-lib"

  val scalaDocExternalMappings = Seq(
    ".*scala.*::scaladoc3::https://scala-lang.org/api/3.x/",
    ".*java.*::javadoc::https://docs.oracle.com/javase/8/docs/api/",
    s".*geny.*::scaladoc3::https://javadoc.io/doc/com.lihaoyi/geny_3/${Deps.geny.dep.version}/"
  ).mkString(",")

  def conditionalScalaDocOptions: T[Seq[String]] = T {
    if (ZincWorkerUtil.isDottyOrScala3(scalaVersion()))
      Seq(
        s"-external-mappings:${scalaDocExternalMappings}"
      )
    else Seq()
  }

  def scalaDocOptions = super.scalaDocOptions() ++ conditionalScalaDocOptions()

}

object os extends Module {

  object jvm extends Cross[OsJvmModule](scalaVersions)
  trait OsJvmModule extends OsModule with MiMaChecks {
    object test extends ScalaTests with OsLibTestModule
    object nohometest extends ScalaTests with OsLibTestModule
  }

  object native extends Cross[OsNativeModule](scalaVersions)
  trait OsNativeModule extends OsModule with ScalaNativeModule {
    def scalaNativeVersion = "0.4.14"
    object test extends ScalaNativeTests with OsLibTestModule {
      def nativeLinkStubs = true
    }
    object nohometest extends ScalaNativeTests with OsLibTestModule
  }

  object watch extends Module {
    object jvm extends Cross[WatchJvmModule](scalaVersions)
    trait WatchJvmModule extends OsLibModule {
      def moduleDeps = super.moduleDeps ++ Seq(os.jvm())
      def ivyDeps = Agg(Deps.jna)
      object test extends ScalaTests with OsLibTestModule {
        def moduleDeps = super.moduleDeps ++ Seq(os.jvm().test)
      }
    }
  }
}
