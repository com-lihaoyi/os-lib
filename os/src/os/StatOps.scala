package os

import java.nio.file.{Files, LinkOption}
import java.nio.file.attribute._

import scala.util.Try


/**
  * Checks whether the given path is a symbolic link
  */
object isLink extends Function1[Path, Boolean]{
  def apply(p: Path) = Files.isSymbolicLink(p.toNIO)
}

/**
  * Checks whether the given path is a regular file
  */
object isFile extends Function1[Path, Boolean]{
  def apply(p: Path) = Files.isRegularFile(p.toNIO)
  def apply(p: Path, followLinks: Boolean = true) = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.isRegularFile(p.toNIO, opts:_*)
  }
}


/**
  * Checks whether the given path is a directory
  */
object isDir extends Function1[Path, Boolean]{
  def apply(p: Path) = Files.isDirectory(p.toNIO)
  def apply(p: Path, followLinks: Boolean = true) = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.isDirectory(p.toNIO, opts:_*)
  }
}

/**
  * Gets the size of the given file
  */
object size extends Function1[Path, Long]{
  def apply(p: Path) = Files.size(p.toNIO)
}

/**
  * Gets the mtime of the given file
  */
object mtime extends Function1[Path, Long]{
  def apply(p: Path) = Files.getLastModifiedTime(p.toNIO).toMillis
  def apply(p: Path, followLinks: Boolean = true) = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.getLastModifiedTime(p.toNIO, opts:_*).toMillis
  }
}


object stat extends Function1[os.Path, os.stat]{
  def apply(p: os.Path) = apply(p, followLinks = true)
  def apply(p: os.Path, followLinks: Boolean = true) = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    os.stat.make(
      // Don't blow up if we stat `root`
      p.segments.lastOption.getOrElse("/"),
      Files.readAttributes(
        p.toNIO,
        classOf[BasicFileAttributes],
        opts:_*
      ),
      Try(Files.readAttributes(
        p.toNIO,
        classOf[PosixFileAttributes],
        opts:_*
      )).toOption
    )
  }
  def make(name: String, attrs: BasicFileAttributes, posixAttrs: Option[PosixFileAttributes]) = {
    import collection.JavaConverters._
    new stat(
      name,
      attrs.size(),
      attrs.lastModifiedTime(),
      posixAttrs.map(_.owner).orNull,
      posixAttrs.map(a => new PermSet(a.permissions.asScala.toSet)).orNull,
      if (attrs.isRegularFile) FileType.File
      else if (attrs.isDirectory) FileType.Dir
      else if (attrs.isSymbolicLink) FileType.SymLink
      else if (attrs.isOther) FileType.Other
      else ???
    )
  }
  object full extends Function1[os.Path, os.stat.full] {
    def apply(p: os.Path) = apply(p, followLinks = true)
    def apply(p: os.Path, followLinks: Boolean = true) = {
      val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
      os.stat.full.make(
        p.segments.lastOption.getOrElse("/"),
        Files.readAttributes(
          p.toNIO,
          classOf[BasicFileAttributes],
          opts:_*
        ),
        Try(Files.readAttributes(
          p.toNIO,
          classOf[PosixFileAttributes],
          opts:_*
        )).toOption
      )
    }
    def make(name: String, attrs: BasicFileAttributes, posixAttrs: Option[PosixFileAttributes]) = {
      import collection.JavaConverters._
      new full(
        name,
        attrs.size(),
        attrs.lastModifiedTime(),
        attrs.lastAccessTime(),
        attrs.creationTime(),
        posixAttrs.map(_.group()).orNull,
        posixAttrs.map(_.owner()).orNull,
        posixAttrs.map(a => new PermSet(a.permissions.asScala.toSet)).orNull,
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
  case class full(name: String,
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
}

/**
  * The result from doing an system `stat` on a particular path.
  *
  * Created via `stat! filePath`.
  *
  * If you want more information, use `stat.full`
  */
case class stat(name: String,
                size: Long,
                mtime: FileTime,
                owner: UserPrincipal,
                permissions: PermSet,
                fileType: FileType){
  def isDir = fileType == FileType.Dir
  def isSymLink = fileType == FileType.SymLink
  def isFile = fileType == FileType.File
}