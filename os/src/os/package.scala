import scala.collection.Seq

package object os extends RelPathStuff{
  implicit val postfixOps = scala.language.postfixOps
  implicit def RegexContextMaker(s: StringContext): RegexContext = new RegexContext(s)

  object RegexContext{
    class Interped(parts: Seq[String]){
      def unapplySeq(s: String) = {
        val Seq(head, tail@_*) = parts.map(java.util.regex.Pattern.quote)

        val regex = head + tail.map("(.*)" + _).mkString
        regex.r.unapplySeq(s)
      }
    }
  }

  /**
    * Lets you pattern match strings with interpolated glob-variables
    */
  class RegexContext(sc: StringContext) {
    def r = new RegexContext.Interped(sc.parts)
  }
  /**
   * The root of the filesystem
   */
  val root = os.Path.root

  def resource(implicit resRoot: ResourceRoot = Thread.currentThread().getContextClassLoader) ={
    os.ResourcePath.resource(resRoot)
  }

  /**
   * The user's home directory
   */
  val home = Path(System.getProperty("user.home"))

  /**
    * Alias for `java.nio.file.Files.createTempFile` and
    * `java.io.File.deleteOnExit`. Pass in `deleteOnExit = false` if you want
    * the temp file to stick around.
    */
  object temp{
    /**
      * Creates a temporary directory
      */
    def dir(dir: Path = null,
            prefix: String = null,
            deleteOnExit: Boolean = true): Path = {
      val nioPath = dir match{
        case null => java.nio.file.Files.createTempDirectory(prefix)
        case _ => java.nio.file.Files.createTempDirectory(dir.toNIO, prefix)
      }
      if (deleteOnExit) nioPath.toFile.deleteOnExit()
      Path(nioPath)
    }

    /**
      * Creates a temporary file with the provided contents
      */
    def apply(contents: Writable = null,
              dir: Path = null,
              prefix: String = null,
              suffix: String = null,
              deleteOnExit: Boolean = true): Path = {

      val nioPath = dir match{
        case null => java.nio.file.Files.createTempFile(prefix, suffix)
        case _ => java.nio.file.Files.createTempFile(dir.toNIO, prefix, suffix)
      }

      if (contents != null) write.over(Path(nioPath), contents)
      if (deleteOnExit) nioPath.toFile.deleteOnExit()
      Path(nioPath)
    }
  }

  /**
   * The current working directory for this process.
   */
  lazy val pwd = os.Path(new java.io.File("").getCanonicalPath)
  @deprecated("replaced by pwd","0.7.5")
  lazy val cwd = pwd

  /**
    * If you want to call subprocesses using [[%]] or [[%%]] and don't care
    * what working directory they use, import this via
    *
    * `import os.ImplicitWd._`
    *
    * To make them use the process's working directory for each subprocess
    */
  object ImplicitWd{
    implicit lazy val implicitCwd = os.pwd
  }

  /**
    * Extractor to let you easily pattern match on [[os.Path]]s. Lets you do
    *
    * {{{
    *   @ val base/segment/filename = pwd
    *   base: Path = Path(Vector("Users", "haoyi", "Dropbox (Personal)"))
    *   segment: String = "Workspace"
    *   filename: String = "Ammonite"
    * }}}
    *
    * To break apart a path and extract various pieces of it.
    */
  object /{
    def unapply[T <: BasePath](p: T): Option[(p.ThisType, String)] = {
      if (p.segments.nonEmpty)
        Some((p / up, p.last))
      else None
    }
  }

  /**
    * Used to spawn a subprocess interactively; any output gets printed to the
    * console and any input gets requested from the current console. Can be
    * used to run interactive subprocesses like `%vim`, `%python`,
    * `%ssh "www.google.com"` or `%sbt`.
    */
  val % = Shellout.%
  /**
    * Spawns a subprocess non-interactively, waiting for it to complete and
    * collecting all output into a [[CommandResult]] which exposes it in a
    * convenient form. Call via `%%('whoami).out.trim` or
    * `%%('git, 'commit, "-am", "Hello!").exitCode`
    */
  val %% = Shellout.%%
}
