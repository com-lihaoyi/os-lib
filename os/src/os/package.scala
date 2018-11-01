import scala.collection.Seq

package object os{
  type Generator[+T] = geny.Generator[T]
  val Generator = geny.Generator
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
  val root = Path(java.nio.file.Paths.get("").toAbsolutePath.getRoot)

  def resource(implicit resRoot: ResourceRoot = Thread.currentThread().getContextClassLoader) ={
    os.ResourcePath.resource(resRoot)
  }

  /**
   * The user's home directory
   */
  val home = Path(System.getProperty("user.home"))



  /**
   * The current working directory for this process.
   */
  val pwd = os.Path(new java.io.File("").getCanonicalPath)

  val up = RelPath.up

  val rel = RelPath.rel
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

  def expandUser[T: PathConvertible](f0: T) = {
    val f = implicitly[PathConvertible[T]].apply(f0)
    if (f.subpath(0, 1).toString != "~") Path(f0)
    else Path(System.getProperty("user.home"))/RelPath(f.subpath(0, 1).relativize(f))
  }

  def expandUser[T: PathConvertible](f0: T, base: Path) = {
    val f = implicitly[PathConvertible[T]].apply(f0)
    if (f.subpath(0, 1).toString != "~") Path(f0, base)
    else Path(System.getProperty("user.home"))/RelPath(f.subpath(0, 1).relativize(f))
  }

}
