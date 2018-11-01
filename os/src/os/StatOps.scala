package os

import java.nio.file.{Files, LinkOption}
import java.nio.file.attribute._

import scala.util.Try


/**
  * Checks whether the given path is a symbolic link
  */
object isLink extends Function1[Path, Boolean]{
  def apply(p: Path): Boolean = Files.isSymbolicLink(p.toNIO)
}

/**
  * Checks whether the given path is a regular file
  */
object isFile extends Function1[Path, Boolean]{
  def apply(p: Path): Boolean = Files.isRegularFile(p.toNIO)
  def apply(p: Path, followLinks: Boolean = true): Boolean = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.isRegularFile(p.toNIO, opts:_*)
  }
}


/**
  * Checks whether the given path is a directory
  */
object isDir extends Function1[Path, Boolean]{
  def apply(p: Path): Boolean = Files.isDirectory(p.toNIO)
  def apply(p: Path, followLinks: Boolean = true): Boolean = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.isDirectory(p.toNIO, opts:_*)
  }
}

/**
  * Gets the size of the given file
  */
object size extends Function1[Path, Long]{
  def apply(p: Path): Long = Files.size(p.toNIO)
}

/**
  * Gets the mtime of the given file
  */
object mtime extends Function1[Path, Long]{
  def apply(p: Path): Long = Files.getLastModifiedTime(p.toNIO).toMillis
  def apply(p: Path, followLinks: Boolean = true): Long = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.getLastModifiedTime(p.toNIO, opts:_*).toMillis
  }
}

/**
  * Reads in the basic filesystem metadata for the given file. By default follows
  * symbolic links to read the metadata of whatever the link is pointing at; set
  * `followLinks = false` to disable that and instead read the metadata of the
  * symbolic link itself.
  */
object stat extends Function1[os.Path, os.StatInfo]{
  def apply(p: os.Path): os.StatInfo = apply(p, followLinks = true)
  def apply(p: os.Path, followLinks: Boolean = true): os.StatInfo = {
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
    new StatInfo(
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

  /**
    * Reads in the full filesystem metadata for the given file. By default follows
    * symbolic links to read the metadata of whatever the link is pointing at; set
    * `followLinks = false` to disable that and instead read the metadata of the
    * symbolic link itself.
    */
  object full extends Function1[os.Path, os.FullStatInfo] {
    def apply(p: os.Path): os.FullStatInfo = apply(p, followLinks = true)
    def apply(p: os.Path, followLinks: Boolean = true): os.FullStatInfo = {
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
      new os.FullStatInfo(
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


}
