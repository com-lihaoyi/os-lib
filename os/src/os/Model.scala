package os

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Paths}
import java.nio.file.attribute._

import scala.io.Codec
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
  * Trivial wrapper around `Array[Byte]` with sane equality and useful toString
  */
class Bytes(val array: Array[Byte]){
  override def equals(other: Any) = other match{
    case otherBytes: Bytes => java.util.Arrays.equals(array, otherBytes.array)
    case _ => false
  }
  override def toString = new String(array, java.nio.charset.StandardCharsets.UTF_8)
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
                         chunks: Seq[Either[Bytes, Bytes]]) {
  /**
    * The standard output and error of the executed command, exposed in a
    * number of ways for convenient access
    */
  val (out, err) = {
    val outChunks = collection.mutable.Buffer.empty[Bytes]
    val errChunks = collection.mutable.Buffer.empty[Bytes]
    chunks.foreach{
      case Left(s) => outChunks.append(s)
      case Right(s) => errChunks.append(s)
    }
    (StreamValue.ChunkStreamValue(outChunks), StreamValue.ChunkStreamValue(errChunks))
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
  * Encapsulates one of the output streams from a subprocess and provides
  * convenience methods for accessing it in a variety of forms
  */
trait StreamValue{
  def bytes: Array[Byte]

  def string: String = string(StandardCharsets.UTF_8)
  def string(codec: Codec): String = new String(bytes, codec.charSet)

  def trim: String = string.trim
  def trim(codec: Codec): String = string(codec).trim

  def lines: Vector[String] = Predef.augmentString(string).lines.toVector
  def lines(codec: Codec): Vector[String] = Predef.augmentString(string(codec)).lines.toVector
}
object StreamValue{
  case class ChunkStreamValue(chunks: Seq[Bytes]) extends StreamValue{
    def bytes = chunks.iterator.map(_.array).toArray.flatten
  }
}
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
  * Created via `stat! filePath`.
  *
  * If you want more information, use `stat.full`
  */
case class BasicStatInfo(size: Long,
                         mtime: FileTime,
                         fileType: FileType){
  def isDir = fileType == FileType.Dir
  def isSymLink = fileType == FileType.SymLink
  def isFile = fileType == FileType.File
}
object BasicStatInfo{

  def make(attrs: BasicFileAttributes) = {
    new BasicStatInfo(
      attrs.size(),
      attrs.lastModifiedTime(),
      if (attrs.isRegularFile) FileType.File
      else if (attrs.isDirectory) FileType.Dir
      else if (attrs.isSymbolicLink) FileType.SymLink
      else if (attrs.isOther) FileType.Other
      else ???
    )
  }
}

/**
  * The result from doing an system `stat` on a particular path.
  *
  * Created via `stat! filePath`.
  *
  * If you want more information, use `stat.full`
  */
case class StatInfo(size: Long,
                    mtime: FileTime,
                    owner: UserPrincipal,
                    permissions: PermSet,
                    fileType: FileType){
  def isDir = fileType == FileType.Dir
  def isSymLink = fileType == FileType.SymLink
  def isFile = fileType == FileType.File
}
object StatInfo{

  def make(attrs: BasicFileAttributes, posixAttrs: Option[PosixFileAttributes]) = {
    new StatInfo(
      attrs.size(),
      attrs.lastModifiedTime(),
      posixAttrs.map(_.owner).orNull,
      posixAttrs.map(a => PermSet.fromSet(a.permissions)).orNull,
      if (attrs.isRegularFile) FileType.File
      else if (attrs.isDirectory) FileType.Dir
      else if (attrs.isSymbolicLink) FileType.SymLink
      else if (attrs.isOther) FileType.Other
      else ???
    )
  }
}
/**
  * A richer, more informative version of the [[stat]] object.
  *
  * Created using `stat.full! filePath`
  */
case class FullStatInfo(size: Long,
                        mtime: FileTime,
                        ctime: FileTime,
                        atime: FileTime,
                        group: GroupPrincipal,
                        owner: UserPrincipal,
                        permissions: PermSet,
                        fileType: FileType){
  override def productPrefix = "stat.full"
  def isDir = fileType == FileType.Dir
  def isSymLink = fileType == FileType.SymLink
  def isFile = fileType == FileType.File
}
object FullStatInfo{

  def make(attrs: BasicFileAttributes, posixAttrs: Option[PosixFileAttributes]) = {
    new os.FullStatInfo(
      attrs.size(),
      attrs.lastModifiedTime(),
      attrs.lastAccessTime(),
      attrs.creationTime(),
      posixAttrs.map(_.group()).orNull,
      posixAttrs.map(_.owner()).orNull,
      posixAttrs.map(a => PermSet.fromSet(a.permissions)).orNull,
      if (attrs.isRegularFile) FileType.File
      else if (attrs.isDirectory) FileType.Dir
      else if (attrs.isSymbolicLink) FileType.SymLink
      else if (attrs.isOther) FileType.Other
      else ???
    )
  }
}