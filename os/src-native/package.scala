import java.nio.file.FileSystem
import java.nio.file.FileSystems
package object os {
  type Generator[+T] = geny.Generator[T]
  val Generator = geny.Generator
  implicit def GlobSyntax(s: StringContext): GlobInterpolator = new GlobInterpolator(s)

  /**
   * The root of the filesystem
   */
  val root: Path = Path(java.nio.file.Paths.get(".").toAbsolutePath.getRoot)

  def root(root: String, fileSystem: FileSystem = FileSystems.getDefault()): Path = {
    val path = Path(fileSystem.getPath(root))
    assert(path.root == root || path.root == root.replace('/', '\\'), s"$root is not a root path")
    path
  }

  private object _home {
    lazy val value = Path(System.getProperty("user.home"))
  }

  /**
   * The user's home directory
   */
  def home: Path = _home.value

  /**
   * The current working directory for this process.
   */
  val pwd: Path = os.Path(java.nio.file.Paths.get(".").toAbsolutePath)

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
  object / {
    def unapply(p: Path): Option[(Path, String)] = {
      if (p.segmentCount != 0) Some((p / up, p.last))
      else None
    }
  }
}
