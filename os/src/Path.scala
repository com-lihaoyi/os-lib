package os

import collection.JavaConverters._

trait PathChunk{
  def segments: Seq[String]
  def ups: Int
}
object PathChunk{
  implicit class RelPathChunk(r: RelPath) extends PathChunk {
    def segments = r.segments
    def ups = r.ups
    override def toString() = r.toString
  }
  implicit class SubPathChunk(r: SubPath) extends PathChunk {
    def segments = r.segments
    def ups = 0
    override def toString() = r.toString
  }
  implicit class StringPathChunk(s: String) extends PathChunk {
    BasePath.checkSegment(s)
    def segments = Seq(s)
    def ups = 0
    override def toString() = s
  }
  implicit class SymbolPathChunk(s: Symbol) extends PathChunk {
    BasePath.checkSegment(s.name)
    def segments = Seq(s.name)
    def ups = 0
    override def toString() = s.name
  }
  implicit class ArrayPathChunk[T](a: Array[T])(implicit f: T => PathChunk) extends PathChunk {
    val inner = SeqPathChunk(a)(f)
    def segments = inner.segments
    def ups = inner.ups

    override def toString() = inner.toString
  }
  implicit class SeqPathChunk[T](a: Seq[T])(implicit f: T => PathChunk) extends PathChunk {
    var segments0 = Nil
    var ups0 = 0
    val (segments, ups) = a.map(f).foldLeft((Seq[String](), 0)){ case ((segments, ups), chunk) =>
      (segments.dropRight(chunk.ups) ++ chunk.segments,
      math.max(chunk.ups - segments.length, 0))
    }

    override def toString() = segments.mkString("/")
  }
}
/**
  * A path which is either an absolute [[Path]], a relative [[RelPath]],
  * or a [[ResourcePath]] with shared APIs and implementations.
  *
  * Most of the filesystem-independent path-manipulation logic that lets you
  * splice paths together or navigate in and out of paths lives in this interface
  */
trait BasePath{
  type ThisType <: BasePath
  /**
    * Combines this path with the given relative path, returning
    * a path of the same type as this one (e.g. `Path` returns `Path`,
    * `RelPath` returns `RelPath`
    */
  def /(chunk: PathChunk): ThisType

  /**
    * Relativizes this path with the given `target` path, finding a
    * relative path `p` such that base/p == this.
    *
    * Note that you can only relativize paths of the same type, e.g.
    * `Path` & `Path` or `RelPath` & `RelPath`. In the case of `RelPath`,
    * this can throw a [[PathError.NoRelativePath]] if there is no
    * relative path that satisfies the above requirement in the general
    * case.
    */
  def relativeTo(target: ThisType): RelPath

  /**
    * Relativizes this path with the given `target` path, finding a
    * sub path `p` such that base/p == this.
    */
  def subRelativeTo(target: ThisType): SubPath = relativeTo(target).asSubPath

  /**
    * This path starts with the target path, including if it's identical
    */
  def startsWith(target: ThisType): Boolean

  /**
    * This path ends with the target path, including if it's identical
    */
  def endsWith(target: RelPath): Boolean

  /**
    * The last segment in this path. Very commonly used, e.g. it
    * represents the name of the file/folder in filesystem paths
    */
  def last: String

  /**
    * Gives you the file extension of this path, or the empty
    * string if there is no extension
    */
  def ext: String

  /**
    * Gives you the base name of this path, ie without the extension
    */
  def baseName: String

  /**
    * The individual path segments of this path.
    */
  def segments: TraversableOnce[String]

}

object BasePath {
  def checkSegment(s: String) = {
    def fail(msg: String) = throw PathError.InvalidSegment(s, msg)
    def considerStr =
      "use the Path(...) or RelPath(...) constructor calls to convert them. "

    s.indexOf('/') match{
      case -1 => // do nothing
      case c => fail(
        s"[/] is not a valid character to appear in a path segment. " +
          "If you want to parse an absolute or relative path that may have " +
          "multiple segments, e.g. path-strings coming from external sources " +
          considerStr
      )

    }
    def externalStr = "If you are dealing with path-strings coming from external sources, "
    s match{
      case "" =>
        fail(
          "OS-Lib does not allow empty path segments " +
            externalStr + considerStr
        )
      case "." =>
        fail(
          "OS-Lib does not allow [.] as a path segment " +
            externalStr + considerStr
        )
      case ".." =>
        fail(
          "OS-Lib does not allow [..] as a path segment " +
            externalStr +
            considerStr +
            "If you want to use the `..` segment manually to represent going up " +
            "one level in the path, use the `up` segment from `os.up` " +
            "e.g. an external path foo/bar/../baz translates into 'foo/'bar/up/'baz."
        )
      case _ =>
    }
  }
  def chunkify(s: java.nio.file.Path) = {
    import collection.JavaConverters._
    s.iterator().asScala.map(_.toString).filter(_ != ".").filter(_ != "").toArray
  }
}

trait SegmentedPath extends BasePath{
  protected[this] def make(p: Seq[String], ups: Int): ThisType
  /**
    * The individual path segments of this path.
    */
  def segments: IndexedSeq[String]

  def /(chunk: PathChunk) = make(
    segments.dropRight(chunk.ups) ++ chunk.segments,
    math.max(chunk.ups - segments.length, 0)
  )

  def endsWith(target: RelPath): Boolean = {
    this == target || (target.ups == 0 && this.segments.endsWith(target.segments))
  }
}

trait BasePathImpl extends BasePath{
  def /(chunk: PathChunk): ThisType

  def ext = {
    val li = last.lastIndexOf('.')
    if (li == -1) ""
    else last.slice(li+1, last.length)
  }

  override def baseName: String = {
    val li = last.lastIndexOf('.')
    if (li == -1) last
    else last.slice(0, li)
  }

  def last: String
}

object PathError{
  type IAE = IllegalArgumentException
  private[this] def errorMsg(s: String, msg: String) =
    s"[$s] is not a valid path segment. $msg"

  case class InvalidSegment(segment: String, msg: String) extends IAE(errorMsg(segment, msg))

  case object AbsolutePathOutsideRoot
    extends IAE("The path created has enough ..s that it would start outside the root directory")

  case class NoRelativePath(src: RelPath, base: RelPath)
    extends IAE(s"Can't relativize relative paths $src from $base")
}

/**
  * Represents a value that is either an absolute [[Path]] or a
  * relative [[RelPath]], and can be constructed from a
  * java.nio.file.Path or java.io.File
  */
sealed trait FilePath extends BasePath{
  def toNIO: java.nio.file.Path
  def resolveFrom(base: os.Path): os.Path
}
object FilePath {
  def apply[T: PathConvertible](f0: T) = {
    val f = implicitly[PathConvertible[T]].apply(f0)
    if (f.isAbsolute) Path(f0)
    else {
      val r = RelPath(f0)
      if (r.ups == 0) r.asSubPath
      else r
    }
  }
}

/**
  * A relative path on the filesystem. Note that the path is
  * normalized and cannot contain any empty or ".". Parent ".."
  * segments can only occur at the left-end of the path, and
  * are collapsed into a single number [[ups]].
  */
class RelPath private[os](segments0: Array[String], val ups: Int)
  extends FilePath with BasePathImpl with SegmentedPath {
  def last = segments.last
  val segments: IndexedSeq[String] = segments0
  type ThisType = RelPath
  require(ups >= 0)
  protected[this] def make(p: Seq[String], ups: Int) = {
    new RelPath(p.toArray[String], ups + this.ups)
  }

  def relativeTo(base: RelPath): RelPath = {
    if (base.ups < ups) new RelPath(segments0, ups + base.segments.length)
    else if (base.ups == ups) SubPath.relativeTo0(segments0, base.segments)
    else throw PathError.NoRelativePath(this, base)
  }

  def startsWith(target: RelPath) = {
    this.segments0.startsWith(target.segments) && this.ups == target.ups
  }

  override def toString = (Seq.fill(ups)("..") ++ segments0).mkString("/")
  override def hashCode = segments.hashCode() + ups.hashCode()
  override def equals(o: Any): Boolean = o match {
    case p: RelPath => segments == p.segments && p.ups == ups
    case p: SubPath => segments == p.segments && ups == 0
    case _ => false
  }

  def toNIO = java.nio.file.Paths.get(toString)

  def asSubPath = {
    require(ups == 0)
    new SubPath(segments0)
  }

  def resolveFrom(base: os.Path) = base / this
}

object RelPath {
  def apply[T: PathConvertible](f0: T): RelPath = {
    val f = implicitly[PathConvertible[T]].apply(f0)

    require(!f.isAbsolute, s"$f is not a relative path")

    val segments = BasePath.chunkify(f.normalize())
    val (ups, rest) = segments.partition(_ == "..")
    new RelPath(rest, ups.length)
  }

  def apply(segments0: IndexedSeq[String], ups: Int) = {
    segments0.foreach(BasePath.checkSegment)
    new RelPath(segments0.toArray, ups)
  }

  import Ordering.Implicits._
  implicit val relPathOrdering: Ordering[RelPath] =
    Ordering.by((rp: RelPath) => (rp.ups, rp.segments.length, rp.segments.toIterable))

  val up: RelPath = new RelPath(Internals.emptyStringArray, 1)
  val rel: RelPath = new RelPath(Internals.emptyStringArray, 0)
  implicit def SubRelPath(p: SubPath): RelPath = new RelPath(p.segments0, 0)
}

/**
  * A relative path on the filesystem, without any `..` or `.` segments
  */
class SubPath private[os](val segments0: Array[String])
  extends FilePath with BasePathImpl with SegmentedPath {
  def last = segments.last
  val segments: IndexedSeq[String] = segments0
  type ThisType = SubPath
  protected[this] def make(p: Seq[String], ups: Int) = {
    require(ups == 0)
    new SubPath(p.toArray[String])
  }

  def relativeTo(base: SubPath): RelPath = SubPath.relativeTo0(segments0, base.segments0)

  def startsWith(target: SubPath) = this.segments0.startsWith(target.segments)

  override def toString = segments0.mkString("/")
  override def hashCode = segments.hashCode()
  override def equals(o: Any): Boolean = o match {
    case p: SubPath => segments == p.segments
    case p: RelPath => segments == p.segments && p.ups == 0
    case _ => false
  }

  def toNIO = java.nio.file.Paths.get(toString)

  def resolveFrom(base: os.Path) = base / this
}

object SubPath {
  private[os] def relativeTo0(segments0: Array[String], segments: IndexedSeq[String]): RelPath = {

    val commonPrefix = {
      val maxSize = scala.math.min(segments0.length, segments.length)
      var i = 0
      while ( i < maxSize && segments0(i) == segments(i)) i += 1
      i
    }
    val newUps = segments.length - commonPrefix

    new RelPath(segments0.drop(commonPrefix), newUps)
  }
  def apply[T: PathConvertible](f0: T): SubPath = RelPath.apply[T](f0).asSubPath

  def apply(segments0: IndexedSeq[String]): SubPath = {
    segments0.foreach(BasePath.checkSegment)
    new SubPath(segments0.toArray)
  }

  import Ordering.Implicits._
  implicit val subPathOrdering: Ordering[SubPath] =
    Ordering.by((rp: SubPath) => (rp.segments.length, rp.segments.toIterable))

  val sub: SubPath = new SubPath(Internals.emptyStringArray)
}

object Path {
  def apply(p: FilePath, base: Path) = p match{
    case p: RelPath => base / p
    case p: SubPath => base / p
    case p: Path => p
  }

  /**
    * Equivalent to [[os.Path.apply]], but automatically expands a
    * leading `~/` into the user's home directory, for convenience
    */
  def expandUser[T: PathConvertible](f0: T, base: Path = null) = {
    val f = implicitly[PathConvertible[T]].apply(f0)
    if (f.subpath(0, 1).toString != "~") if (base == null) Path(f0) else Path(f0, base)
    else {
      Path(System.getProperty("user.home"))(PathConvertible.StringConvertible)/
        RelPath(f.subpath(0, 1).relativize(f))(PathConvertible.NioPathConvertible)
    }
  }

  def apply[T: PathConvertible](f: T, base: Path): Path = apply(FilePath(f), base)
  def apply[T: PathConvertible](f0: T): Path = {
    val f = implicitly[PathConvertible[T]].apply(f0)
    if (f.iterator.asScala.count(_.startsWith("..")) > f.getNameCount/ 2) {
      throw PathError.AbsolutePathOutsideRoot
    }

    val normalized = f.normalize()
    new Path(normalized)
  }

  implicit val pathOrdering: Ordering[Path] = new Ordering[Path]{
    def compare(x: Path, y: Path): Int = {
      val xSegCount = x.segmentCount
      val ySegCount = y.segmentCount
      if (xSegCount < ySegCount) -1
      else if (xSegCount > ySegCount) 1
      else if (xSegCount == 0 && ySegCount == 0) 0
      else{
        var xSeg = ""
        var ySeg = ""
        var i = -1
        while ({
          i += 1
          xSeg = x.getSegment(i)
          ySeg = y.getSegment(i)
          i < xSegCount && xSeg == ySeg
        }) ()
        if (i == xSegCount) 0
        else Ordering.String.compare(xSeg, ySeg)
      }
    }
  }

}

trait ReadablePath{
  def toSource: os.Source
  def getInputStream: java.io.InputStream
}

/**
  * An absolute path on the filesystem. Note that the path is
  * normalized and cannot contain any empty `""`, `"."` or `".."` segments
  */
class Path private[os](val wrapped: java.nio.file.Path)
  extends FilePath with ReadablePath with BasePathImpl {
  def toSource: SeekableSource =
    new SeekableSource.ChannelSource(java.nio.file.Files.newByteChannel(wrapped))

  require(wrapped.isAbsolute, s"$wrapped is not an absolute path")
  def segments: Iterator[String] = wrapped.iterator().asScala.map(_.toString)
  def getSegment(i: Int): String = wrapped.getName(i).toString
  def segmentCount = wrapped.getNameCount
  type ThisType = Path

  def last = wrapped.getFileName.toString

  def /(chunk: PathChunk): ThisType = {
    if (chunk.ups > wrapped.getNameCount) throw PathError.AbsolutePathOutsideRoot
    val resolved = wrapped.resolve(chunk.toString).normalize()
    new Path(resolved)
  }
  override def toString = wrapped.toString

  override def equals(o: Any): Boolean = o match {
    case p: Path => wrapped.equals(p.wrapped)
    case _ => false
  }
  override def hashCode = wrapped.hashCode()

  def startsWith(target: Path) = wrapped.startsWith(target.wrapped)

  def endsWith(target: RelPath) = wrapped.endsWith(target.toString)

  def relativeTo(base: Path): RelPath = {

    val nioRel = base.wrapped.relativize(wrapped)
    val segments = nioRel.iterator().asScala.map(_.toString).toArray match{
      case Array("") => Internals.emptyStringArray
      case arr => arr
    }
    val nonUpIndex = segments.indexWhere(_ != "..") match{
      case -1 => segments.length
      case n => n
    }

    new RelPath(segments.drop(nonUpIndex), nonUpIndex)
  }

  def toIO: java.io.File = wrapped.toFile
  def toNIO: java.nio.file.Path = wrapped

  def resolveFrom(base: os.Path) = this

  def getInputStream = java.nio.file.Files.newInputStream(wrapped)
}

sealed trait PathConvertible[T]{
  def apply(t: T): java.nio.file.Path
}

object PathConvertible{
  implicit object StringConvertible extends PathConvertible[String]{
    def apply(t: String) =
      java.nio.file.Paths.get(if(t.matches("\\/[A-z]:\\/.*")) t.substring(1) else t)
  }
  implicit object JavaIoFileConvertible extends PathConvertible[java.io.File]{
    def apply(t: java.io.File) = java.nio.file.Paths.get(t.getPath)
  }
  implicit object NioPathConvertible extends PathConvertible[java.nio.file.Path]{
    def apply(t: java.nio.file.Path) = t
  }
}
