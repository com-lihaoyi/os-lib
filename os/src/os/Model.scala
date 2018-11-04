package os

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Paths}
import java.nio.file.attribute._

import collection.JavaConverters._
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
  implicit def fromSet(arg: Set[PosixFilePermission]): PermSet = {
    new PermSet(arg)
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
    val perms = new java.util.HashSet[PosixFilePermission]()
    def add(i: Int, expected: Char, perm: PosixFilePermission) = {
      if(arg(i) == expected) perms.add(perm)
      else if (arg(i) != '-') {
        throw new Exception(
          "Invalid permissions string: unknown character [" + arg(i) + "] " +
            "at index " + i + ". Must be [-] or [" + expected + "]."
        )
      }
    }
    add(0, 'r', OWNER_READ)
    add(1, 'w', OWNER_WRITE)
    add(2, 'x', OWNER_EXECUTE)
    add(3, 'r', GROUP_READ)
    add(4, 'w', GROUP_WRITE)
    add(5, 'x', GROUP_EXECUTE)
    add(6, 'r', OTHERS_READ)
    add(7, 'w', OTHERS_WRITE)
    add(8, 'x', OTHERS_EXECUTE)
    new PermSet(perms.asScala.toSet)
  }

  /**
    * Parses a 0x777 integer into a [[PermSet]]
    */
  implicit def fromInt(arg: Int): PermSet = {
    import PosixFilePermission._
    val perms = new java.util.HashSet[PosixFilePermission]()
    def add(i: Int, perm: PosixFilePermission) = {
      if((arg & (256 >> i)) != 0) perms.add(perm)
    }
    add(0, OWNER_READ)
    add(1, OWNER_WRITE)
    add(2, OWNER_EXECUTE)
    add(3, GROUP_READ)
    add(4, GROUP_WRITE)
    add(5, GROUP_EXECUTE)
    add(6, OTHERS_READ)
    add(7, OTHERS_WRITE)
    add(8, OTHERS_EXECUTE)
    new PermSet(perms.asScala.toSet)
  }
}

/**
  * A set of permissions; can be converted easily to the rw-rwx-r-x form via
  * [[toString]], or to the 0x777 form via [[toInt]] and the other way via
  * `PermSet.fromString`/`PermSet.fromInt`
  *
  */
class PermSet(val value: Set[PosixFilePermission]) {
  def contains(elem: PosixFilePermission) = value.contains(elem)
  def +(elem: PosixFilePermission) = new PermSet(value + elem)
  def -(elem: PosixFilePermission) = new PermSet(value - elem)
  def iterator = value.iterator
  def toInt(): Int = {
    var total = 0
    import PosixFilePermission._
    def add(i: Int, perm: PosixFilePermission) = {
      if (value.contains(perm)) total += (256 >> i)
    }
    add(0, OWNER_READ)
    add(1, OWNER_WRITE)
    add(2, OWNER_EXECUTE)
    add(3, GROUP_READ)
    add(4, GROUP_WRITE)
    add(5, GROUP_EXECUTE)
    add(6, OTHERS_READ)
    add(7, OTHERS_WRITE)
    add(8, OTHERS_EXECUTE)
    total
  }
  override def toString() = {
    PosixFilePermissions.toString(value.asJava)
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
    (StreamValue(outChunks), StreamValue(errChunks))
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
case class StreamValue(chunks: Seq[Bytes]){
  def bytes = chunks.iterator.map(_.array).toArray.flatten

  def string: String = string(StandardCharsets.UTF_8)
  def string(codec: Codec): String = new String(bytes, codec.charSet)

  def trim: String = string.trim
  def trim(codec: Codec): String = string(codec).trim

  def lines: Vector[String] = Predef.augmentString(string).lines.toVector
  def lines(codec: Codec): Vector[String] = Predef.augmentString(string(codec)).lines.toVector
}
/**
  * An implicit wrapper defining the things that can
  * be "interpolated" directly into a subprocess call.
  */
case class Shellable(s: Seq[String])
object Shellable{
  implicit def StringShellable(s: String): Shellable = Shellable(Seq(s))

  implicit def SymbolShellable(s: Symbol): Shellable = Shellable(Seq(s.name))
  implicit def PathShellable(s: Path): Shellable = Shellable(Seq(s.toString))
  implicit def RelPathShellable(s: RelPath): Shellable = Shellable(Seq(s.toString))
  implicit def NumericShellable[T: Numeric](s: T): Shellable = Shellable(Seq(s.toString))

  implicit def IterableShellable[T](s: Iterable[T])(implicit f: T => Shellable): Shellable =
    Shellable(s.toSeq.flatMap(f(_).s))

  implicit def ArrayShellable[T](s: Array[T])(implicit f: T => Shellable): Shellable =
    Shellable(s.flatMap(f(_).s))
}

/**
  * The different ways you can redirect input/output streams from a suprocess
  */
sealed trait Redirect{
  def toRedirectTo: ProcessBuilder.Redirect
  def toRedirectFrom: ProcessBuilder.Redirect
}

object Redirect{
  implicit def RedirectToPath(p: Path): Redirect = RedirectToPath(p)
}

/**
  * Inherit the input/output stream from the current process
  */
object Inherit extends Redirect{
  def toRedirectTo = ProcessBuilder.Redirect.INHERIT
  def toRedirectFrom = ProcessBuilder.Redirect.INHERIT
}

/**
  * Pipe the input/output stream to the current process to be used via
  * `java.lang.Process#{getInputStream,getOutputStream,getErrorStream}`
  */
object Pipe extends Redirect{
  def toRedirectTo = ProcessBuilder.Redirect.PIPE
  def toRedirectFrom = ProcessBuilder.Redirect.PIPE
}

/**
  * Redirect the input/output directly to a file on disk
  */
case class RedirectToPath(p: Path, append: Boolean = false) extends Redirect{
  def toRedirectTo =
    if (append) ProcessBuilder.Redirect.appendTo(p.toIO)
    else ProcessBuilder.Redirect.to(p.toIO)

  def toRedirectFrom = ProcessBuilder.Redirect.from(p.toIO)
}


/**
  * The result from doing an system `stat` on a particular path.
  *
  * Created via `stat! filePath`.
  *
  * If you want more information, use `stat.full`
  */
case class StatInfo(name: String,
                    size: Long,
                    mtime: FileTime,
                    owner: UserPrincipal,
                    permissions: PermSet,
                    fileType: FileType){
  def isDir = fileType == FileType.Dir
  def isSymLink = fileType == FileType.SymLink
  def isFile = fileType == FileType.File
}

/**
  * A richer, more informative version of the [[stat]] object.
  *
  * Created using `stat.full! filePath`
  */
case class FullStatInfo(name: String,
                        size: Long,
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