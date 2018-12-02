package os

import collection.JavaConverters._


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
  def /(subpath: RelPath): ThisType

  /**
    * Relativizes this path with the given `base` path, finding a
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
          "Ammonite-Ops does not allow empty path segments " +
            externalStr + considerStr
        )
      case "." =>
        fail(
          "Ammonite-Ops does not allow [.] as a path segment " +
            externalStr + considerStr
        )
      case ".." =>
        fail(
          "Ammonite-Ops does not allow [..] as a path segment " +
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

  def /(subpath: RelPath) = make(
    segments.dropRight(subpath.ups) ++ subpath.segments,
    math.max(subpath.ups - segments.length, 0)
  )

  def endsWith(target: RelPath): Boolean = {
    this == target || (target.ups == 0 && this.segments.endsWith(target.segments))
  }
}
trait BasePathImpl extends BasePath{
  def /(subpath: RelPath): ThisType

  def ext = {
    if (!last.contains('.')) ""
    else last.split('.').lastOption.getOrElse("")
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
trait FilePath extends BasePath{
  def toNIO: java.nio.file.Path
}
object FilePath {
  def apply[T: PathConvertible](f0: T) = {
    val f = implicitly[PathConvertible[T]].apply(f0)
    if (f.isAbsolute) Path(f0)
    else RelPath(f0)
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
    if (base.ups < ups) {
      new RelPath(segments0, ups + base.segments.length)
    } else if (base.ups == ups) {
      val commonPrefix = {
        val maxSize = scala.math.min(segments0.length, base.segments.length)
        var i = 0
        while ( i < maxSize && segments0(i) == base.segments(i)) i += 1
        i
      }
      val newUps = base.segments.length - commonPrefix

      new RelPath(segments0.drop(commonPrefix), ups + newUps)
    } else throw PathError.NoRelativePath(this, base)
  }

  def startsWith(target: RelPath) = {
    this.segments0.startsWith(target.segments) && this.ups == target.ups
  }

  override def toString = (Seq.fill(ups)("..") ++ segments0).mkString("/")
  override def hashCode = segments.hashCode() + ups.hashCode()
  override def equals(o: Any): Boolean = o match {
    case p: RelPath => segments == p.segments && p.ups == ups
    case _ => false
  }

  def toNIO = java.nio.file.Paths.get(toString)
}

object RelPath {
  def apply[T: PathConvertible](f0: T): RelPath = {
    val f = implicitly[PathConvertible[T]].apply(f0)

    require(!f.isAbsolute, f + " is not an relative path")

    val segments = BasePath.chunkify(f.normalize())
    val (ups, rest) = segments.partition(_ == "..")
    new RelPath(rest, ups.length)
  }

  implicit def SymPath(s: Symbol): RelPath = StringPath(s.name)
  implicit def StringPath(s: String): RelPath = {
    BasePath.checkSegment(s)
    new RelPath(Array(s), 0)

  }
  def apply(segments0: IndexedSeq[String], ups: Int) = {
    segments0.foreach(BasePath.checkSegment)
    new RelPath(segments0.toArray, ups)
  }

  implicit def IterablePath[T](s: Iterable[T])(implicit conv: T => RelPath): RelPath = {
    s.foldLeft(rel){_ / _}
  }

  implicit def ArrayPath[T](s: Array[T])(implicit conv: T => RelPath): RelPath = IterablePath(s)

  implicit val relPathOrdering: Ordering[RelPath] =
    Ordering.by((rp: RelPath) => (rp.ups, rp.segments.length, rp.segments.toIterable))

  val up: RelPath = new RelPath(Internals.emptyStringArray, 1)
  val rel: RelPath = new RelPath(Internals.emptyStringArray, 0)
}

object Path {
  def apply(p: FilePath, base: Path) = p match{
    case p: RelPath => base/p
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
        do{
          i += 1
          xSeg = x.getSegment(i)
          ySeg = y.getSegment(i)
        } while (i < xSegCount && xSeg == ySeg)
        if (i == xSegCount) 0
        else Ordering.String.compare(xSeg, ySeg)
      }
    }
  }

}

trait ReadablePath{
  def toSource: os.Source
}

/**
  * An absolute path on the filesystem. Note that the path is
  * normalized and cannot contain any empty `""`, `"."` or `".."` segments
  */
class Path private[os](val wrapped: java.nio.file.Path)
  extends FilePath with ReadablePath with BasePathImpl {
  def toSource: SeekableSource =
    new SeekableSource.ChannelSource(java.nio.file.Files.newByteChannel(wrapped))

  require(wrapped.isAbsolute, wrapped + " is not an absolute path")
  def segments: Iterator[String] = wrapped.iterator().asScala.map(_.toString)
  def getSegment(i: Int): String = wrapped.getName(i).toString
  def segmentCount = wrapped.getNameCount
  type ThisType = Path

  def last = wrapped.getFileName.toString

  def /(subpath: RelPath): ThisType = {
    if (subpath.ups > wrapped.getNameCount) throw PathError.AbsolutePathOutsideRoot
    val resolved = wrapped.resolve(subpath.toString).normalize()
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
}

sealed trait PathConvertible[T]{
  def apply(t: T): java.nio.file.Path
}

object PathConvertible{
  implicit object StringConvertible extends PathConvertible[String]{
    def apply(t: String) = java.nio.file.Paths.get(t)
  }
  implicit object JavaIoFileConvertible extends PathConvertible[java.io.File]{
    def apply(t: java.io.File) = java.nio.file.Paths.get(t.getPath)
  }
  implicit object NioPathConvertible extends PathConvertible[java.nio.file.Path]{
    def apply(t: java.nio.file.Path) = t
  }
}