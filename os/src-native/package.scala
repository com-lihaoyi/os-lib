import java.nio.file.FileSystem
import java.nio.file.FileSystems
import scala.util.DynamicVariable
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

  // See https://github.com/com-lihaoyi/os-lib/pull/239
  // and https://github.com/lightbend/mima/issues/794
  // why the need the inner object to preserve binary compatibility
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
  def pwd: Path = dynamicPwdFunction.value()

  private val pwd0 = os.Path(java.nio.file.Paths.get(".").toAbsolutePath)

  /**
   * Used to override `pwd` within a certain scope with a generated value
   */
  val dynamicPwdFunction: DynamicVariable[() => Path] = new DynamicVariable(() => dynamicPwd.value)

  /**
   * Used to override `pwd` within a certain scope with a fixed value
   */
  val dynamicPwd: DynamicVariable[Path] = new DynamicVariable(pwd0)

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
