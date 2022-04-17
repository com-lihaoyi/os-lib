package os

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Paths}
import java.nio.file.attribute._

import scala.io.Codec
import scala.language.implicitConversions
import scala.util.Try

/**
  * Simple enum with the possible filesystem objects a path can resolve to
  */
sealed trait FileType
object FileType{
  case object File extends FileType
  case object Dir extends FileType
  case object SymLink extends FileType
  case object Other extends FileType
}
object PermSet{
  implicit def fromSet(value: java.util.Set[PosixFilePermission]): PermSet = {
    var total = 0
    import PosixFilePermission._
    def add(perm: PosixFilePermission) = {
      if (value.contains(perm)) total += permToMask(perm)
    }
    add(OWNER_READ)
    add(OWNER_WRITE)
    add(OWNER_EXECUTE)
    add(GROUP_READ)
    add(GROUP_WRITE)
    add(GROUP_EXECUTE)
    add(OTHERS_READ)
    add(OTHERS_WRITE)
    add(OTHERS_EXECUTE)

    new PermSet(total)
  }

  /**
    * Parses a `rwx-wxr-w` string into a [[PermSet]]
    */
  implicit def fromString(arg: String): PermSet = {
    require(
      arg.length == 9,
      "Invalid permissions string: must be length 9, not " + arg.length
    )
    import PosixFilePermission._
    var perms = 0
    def add(perm: PosixFilePermission) = {
      val i = permToOffset(perm)
      val expected = permToChar(perm)
      if(arg(i) == expected) perms |= permToMask(perm)
      else if (arg(i) != '-') {
        throw new Exception(
          "Invalid permissions string: unknown character [" + arg(i) + "] " +
            "at index " + i + ". Must be [-] or [" + expected + "]."
        )
      }
    }
    add(OWNER_READ)
    add(OWNER_WRITE)
    add(OWNER_EXECUTE)
    add(GROUP_READ)
    add(GROUP_WRITE)
    add(GROUP_EXECUTE)
    add(OTHERS_READ)
    add(OTHERS_WRITE)
    add(OTHERS_EXECUTE)
    new PermSet(perms)
  }

  /**
    * Parses a 0x777 integer into a [[PermSet]]
    */
  implicit def fromInt(value: Int): PermSet = new PermSet(value)

  def permToMask(elem: PosixFilePermission) = 256 >> permToOffset(elem)
  def permToChar(elem: PosixFilePermission) = elem match{
    case PosixFilePermission.OWNER_READ => 'r'
    case PosixFilePermission.OWNER_WRITE => 'w'
    case PosixFilePermission.OWNER_EXECUTE => 'x'
    case PosixFilePermission.GROUP_READ => 'r'
    case PosixFilePermission.GROUP_WRITE => 'w'
    case PosixFilePermission.GROUP_EXECUTE => 'x'
    case PosixFilePermission.OTHERS_READ => 'r'
    case PosixFilePermission.OTHERS_WRITE => 'w'
    case PosixFilePermission.OTHERS_EXECUTE => 'x'
  }
  def permToOffset(elem: PosixFilePermission) = elem match{
    case PosixFilePermission.OWNER_READ => 0
    case PosixFilePermission.OWNER_WRITE => 1
    case PosixFilePermission.OWNER_EXECUTE => 2
    case PosixFilePermission.GROUP_READ => 3
    case PosixFilePermission.GROUP_WRITE => 4
    case PosixFilePermission.GROUP_EXECUTE => 5
    case PosixFilePermission.OTHERS_READ => 6
    case PosixFilePermission.OTHERS_WRITE => 7
    case PosixFilePermission.OTHERS_EXECUTE => 8
  }
}

/**
  * A set of permissions; can be converted easily to the rw-rwx-r-x form via
  * [[toString]], or to a set of [[PosixFilePermission]]s via [[toSet]] and the
  * other way via `PermSet.fromString`/`PermSet.fromSet`
  */
case class PermSet(value: Int) {
  def contains(elem: PosixFilePermission) = (PermSet.permToMask(elem) & value) != 0
  def +(elem: PosixFilePermission) = new PermSet(value | PermSet.permToMask(elem))
  def ++(other: PermSet) = new PermSet(value | other.value)

  def -(elem: PosixFilePermission) = new PermSet(value & (~PermSet.permToMask(elem)))
  def --(other: PermSet) = new PermSet(value & (~other.value))

  def toInt(): Int = value

  def toSet(): java.util.Set[PosixFilePermission] = {
    import PosixFilePermission._
    val perms = new java.util.HashSet[PosixFilePermission]()
    def add(perm: PosixFilePermission) = {
      if((value & PermSet.permToMask(perm)) != 0) perms.add(perm)
    }
    add(OWNER_READ)
    add(OWNER_WRITE)
    add(OWNER_EXECUTE)
    add(GROUP_READ)
    add(GROUP_WRITE)
    add(GROUP_EXECUTE)
    add(OTHERS_READ)
    add(OTHERS_WRITE)
    add(OTHERS_EXECUTE)
    perms
  }

  override def toString() = {
    import PosixFilePermission._
    def add(perm: PosixFilePermission) = {
      val c = PermSet. permToChar(perm)
      if ((PermSet.permToMask(perm) & value) != 0) c else '-'
    }
    new String(
      Array[Char](
        add(OWNER_READ),
        add(OWNER_WRITE),
        add(OWNER_EXECUTE),
        add(GROUP_READ),
        add(GROUP_WRITE),
        add(GROUP_EXECUTE),
        add(OTHERS_READ),
        add(OTHERS_WRITE),
        add(OTHERS_EXECUTE)
      )
    )
  }
}

/**
  * Contains the accumulated output for the invocation of a subprocess command.
  *
  * Apart from the exit code, the primary data-structure is a sequence of byte
  * chunks, tagged with [[Left]] for stdout and [[Right]] for stderr. This is
  * interleaved roughly in the order it was emitted by the subprocess, and
  * reflects what a user would have see if the subprocess was run manually.
  *
  * Derived from that, is the aggregate `out` and `err` [[StreamValue]]s,
  * wrapping stdout/stderr respectively, and providing convenient access to
  * the aggregate output of each stream, as bytes or strings or lines.
  */
case class CommandResult(exitCode: Int,
                         chunks: Seq[Either[geny.Bytes, geny.Bytes]]) {
  /**
    * The standard output and error of the executed command, exposed in a
    * number of ways for convenient access
    */
  val (out, err) = {
    val outChunks = collection.mutable.Buffer.empty[geny.Bytes]
    val errChunks = collection.mutable.Buffer.empty[geny.Bytes]
    chunks.foreach{
      case Left(s) => outChunks.append(s)
      case Right(s) => errChunks.append(s)
    }
    (geny.ByteData.Chunks(outChunks.toSeq), geny.ByteData.Chunks(errChunks.toSeq))
  }

  override def toString() = {
    s"CommandResult $exitCode\n" +
      chunks.iterator
        .collect{case Left(s) => s case Right(s) => s}
        .map(x => new String(x.array))
        .mkString
  }
}

/**
  * Thrown when a shellout command results in a non-zero exit code.
  *
  * Doesn't contain any additional information apart from the [[CommandResult]]
  * that is normally returned, but ensures that failures in subprocesses happen
  * loudly and won't get ignored unless intentionally caught
  */
case class SubprocessException(result: CommandResult) extends Exception(result.toString)

/**
  * An implicit wrapper defining the things that can
  * be "interpolated" directly into a subprocess call.
  */
case class Shellable(value: Seq[String])
object Shellable{
  implicit def StringShellable(s: String): Shellable = Shellable(Seq(s))

  implicit def SymbolShellable(s: Symbol): Shellable = Shellable(Seq(s.name))
  implicit def PathShellable(s: Path): Shellable = Shellable(Seq(s.toString))
  implicit def RelPathShellable(s: RelPath): Shellable = Shellable(Seq(s.toString))
  implicit def NumericShellable[T: Numeric](s: T): Shellable = Shellable(Seq(s.toString))

  implicit def IterableShellable[T](s: Iterable[T])(implicit f: T => Shellable): Shellable =
    Shellable(s.toSeq.flatMap(f(_).value))

  implicit def ArrayShellable[T](s: Array[T])(implicit f: T => Shellable): Shellable =
    Shellable(s.flatMap(f(_).value))
}

/**
  * The result from doing an system `stat` on a particular path.
  *
  * Note: ctime is not same as ctime (Change Time) in `stat`,
  *       it is creation time maybe fall back to mtime if system not supported it.
  *
  * Created via `stat! filePath`.
  *
  * If you want more information, use `stat.full`
  */
case class StatInfo(size: Long,
                    mtime: FileTime,
                    ctime: FileTime,
                    atime: FileTime,
                    fileType: FileType){
  def isDir = fileType == FileType.Dir
  def isSymLink = fileType == FileType.SymLink
  def isFile = fileType == FileType.File
}
object StatInfo{

  def make(attrs: BasicFileAttributes) = {
    new StatInfo(
      attrs.size(),
      attrs.lastModifiedTime(),
      attrs.creationTime(),
      attrs.lastAccessTime(),
      if (attrs.isRegularFile) FileType.File
      else if (attrs.isDirectory) FileType.Dir
      else if (attrs.isSymbolicLink) FileType.SymLink
      else if (attrs.isOther) FileType.Other
      else ???
    )
  }
}

case class PosixStatInfo(owner: UserPrincipal, permissions: PermSet)
object PosixStatInfo{
  def make(posixAttrs: PosixFileAttributes) = {
    PosixStatInfo(
      posixAttrs.owner,
      PermSet.fromSet(posixAttrs.permissions)
    )
  }
}
