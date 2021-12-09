
package object os{
  type Generator[+T] = geny.Generator[T]
  val Generator = geny.Generator
  implicit def GlobSyntax(s: StringContext): GlobInterpolator = new GlobInterpolator(s)

  /**
   * The root of the filesystem
   */
  val root: Path = Path(java.nio.file.Paths.get(".").toAbsolutePath.getRoot)

  /**
   * The user's home directory
   */
  val home: Path = Path(System.getProperty("user.home"))

  private val initialWorkingDirectory: Path = os.Path(java.nio.file.Paths.get(".").toAbsolutePath)

  private var cwdSupplier: () => Path = () => initialWorkingDirectory

  def setCwdSupplier(supplier: () => Path) = { cwdSupplier = supplier }

  /**
   * The current working directory for this process.
   */
  def pwd: Path = cwdSupplier()

  val up: RelPath = RelPath.up

  val rel: RelPath = RelPath.rel

  val sub: SubPath = SubPath.sub
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
    def unapply(p: Path): Option[(Path, String)] = {
      if (p.segmentCount != 0) Some((p / up, p.last))
      else None
    }
  }
}
