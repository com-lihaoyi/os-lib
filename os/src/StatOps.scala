package os

import java.nio.file.{Files, LinkOption}
import java.nio.file.attribute._

import scala.util.Try

/**
 * Checks whether the given path is a symbolic link
 *
 * Returns `false` if the path does not exist
 */
object isLink extends Function1[Path, Boolean] {
  def apply(p: Path): Boolean = Files.isSymbolicLink(p.wrapped)
}

/**
 * Checks whether the given path is a regular file
 *
 * Returns `false` if the path does not exist
 */
object isFile extends Function1[Path, Boolean] {
  def apply(p: Path): Boolean = Files.isRegularFile(p.wrapped)
  def apply(p: Path, followLinks: Boolean = true): Boolean = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.isRegularFile(p.wrapped, opts: _*)
  }
}

/**
 * Checks whether the given path is a directory
 *
 * Returns `false` if the path does not exist
 */
object isDir extends Function1[Path, Boolean] {
  def apply(p: Path): Boolean = Files.isDirectory(p.wrapped)
  def apply(p: Path, followLinks: Boolean = true): Boolean = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.isDirectory(p.wrapped, opts: _*)
  }
}

/**
 * Checks whether the given path is readable
 *
 * Returns `false` if the path does not exist
 */
object isReadable extends Function1[Path, Boolean] {
  def apply(p: Path): Boolean = Files.isReadable(p.wrapped)
}

/**
 * Checks whether the given path is writable
 *
 * Returns `false` if the path does not exist
 */
object isWritable extends Function1[Path, Boolean] {
  def apply(p: Path): Boolean = Files.isWritable(p.wrapped)
}

/**
 * Checks whether the given path is executable
 *
 * Returns `false` if the path does not exist
 */
object isExecutable extends Function1[Path, Boolean] {
  def apply(p: Path): Boolean = Files.isExecutable(p.wrapped)
}

/**
 * Gets the size of the given file or folder
 *
 * Throws an exception if the file or folder does not exist
 *
 * When called on folders, returns the size of the folder metadata
 * (i.e. the list of children names), and not the size of the folder's
 * recursive contents. Use [[os.walk]] if you want to sum up the total
 * size of a directory tree.
 */
object size extends Function1[Path, Long] {
  def apply(p: Path): Long = Files.size(p.wrapped)
}

/**
 * Gets the mtime of the given file or directory
 */
object mtime extends Function1[Path, Long] {
  def apply(p: Path): Long = Files.getLastModifiedTime(p.wrapped).toMillis
  def apply(p: Path, followLinks: Boolean = true): Long = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.getLastModifiedTime(p.wrapped, opts: _*).toMillis
  }

  /**
   * Sets the mtime of the given file.
   *
   * Note that this always follows links to set the mtime of the referred-to file.
   * Unfortunately there is no Java API to set the mtime of the link itself:
   *
   * https://stackoverflow.com/questions/17308363/symlink-lastmodifiedtime-in-java-1-7
   */
  object set {
    def apply(p: Path, millis: Long) = {
      checker.value.onWrite(p)
      Files.setLastModifiedTime(p.wrapped, FileTime.fromMillis(millis))
    }
  }
}

/**
 * Reads in the basic filesystem metadata for the given file. By default follows
 * symbolic links to read the metadata of whatever the link is pointing at; set
 * `followLinks = false` to disable that and instead read the metadata of the
 * symbolic link itself.
 *
 * Throws an exception if the file or folder does not exist
 */
object stat extends Function1[os.Path, os.StatInfo] {
  def apply(p: os.Path): os.StatInfo = apply(p, followLinks = true)
  def apply(p: os.Path, followLinks: Boolean = true): os.StatInfo = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    os.StatInfo.make(Files.readAttributes(p.wrapped, classOf[BasicFileAttributes], opts: _*))
  }

  /**
   * Reads POSIX metadata for the given file: ownership and permissions data
   */
  object posix {
    def apply(p: os.Path): os.PosixStatInfo = apply(p, followLinks = true)
    def apply(p: os.Path, followLinks: Boolean = true): os.PosixStatInfo = {
      val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
      os.PosixStatInfo.make(Files.readAttributes(p.wrapped, classOf[PosixFileAttributes], opts: _*))
    }
  }
}
