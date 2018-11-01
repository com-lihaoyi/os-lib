/**
 * Basic operations that take place on files. Intended to be
 * both light enough to use from the command line as well as
 * powerful and flexible enough to use in real applications to
 * perform filesystem operations
 */
package os

import java.nio.file._
import java.nio.file.attribute.{FileAttribute, PosixFilePermission, PosixFilePermissions}

import scala.util.Try


/**
 * Create a single directory at the specified path. Optionally takes in a
  * [[PermSet]] to specify the filesystem permissions of the created
  * directory.
  *
  * Errors out if the directory already exists, or if the parent directory of the
  * specified path does not exist. To automatically create enclosing directories and
  * ignore the destination if it already exists, using [[os.makeDir.all]]
 */
object makeDir extends Function1[Path, Unit]{
  def apply(path: Path): Unit = Files.createDirectory(path.toNIO)
  def apply(path: Path, perms: PermSet): Unit = {
    import collection.JavaConverters._
    Files.createDirectory(
      path.toNIO,
      PosixFilePermissions.asFileAttribute(perms.value.asJava)
    )
  }
  /**
    * Similar to [[os.makeDir]], but automatically creates any necessary
    * enclosing directories if they do not exist, and does not raise an error if the
    * destination path already containts a directory
    */
  object all extends Function1[Path, Unit]{
    def apply(path: Path): Unit = Files.createDirectories(path.toNIO)
    def apply(path: Path, perms: PermSet): Unit = {
      if (perms == null) apply(path)
      else {
        import collection.JavaConverters._
        Files.createDirectories(
          path.toNIO,
          PosixFilePermissions.asFileAttribute(perms.value.asJava)
        )
      }
    }
  }
}


trait CopyMove extends Function2[Path, Path, Unit]{

  /**
    * Copy or move a file into a particular folder, rather
    * than into a particular path
    */
  object into extends Function2[Path, Path, Unit]{
    def apply(from: Path, to: Path) = {
      CopyMove.this(from, to/from.last)
    }
  }

  /**
    * Copy or move a file, stomping over anything
    * that may have been there before
    */
  object over extends Function2[Path, Path, Unit]{
    def apply(from: Path, to: Path) = {
      remove(to)
      CopyMove.this(from, to)
    }
  }
}

/**
  * Moves a file or folder from one path to another. Errors out if the destination
  * path already exists, or is within the source path.
 */
object move extends Function2[Path, Path, Unit] with Internals.Mover with CopyMove{
  def apply(from: Path, to: Path): Unit = {
    require(
      !to.startsWith(from),
      s"Can't move a directory into itself: $to is inside $from"
    )
    java.nio.file.Files.move(from.toNIO, to.toNIO)
  }


  def check = false

  object all extends Internals.Mover{
    def check = true
  }
}

/**
  * Copy a file or folder from one path to another. Recursively copies folders with
  * all their contents. Errors out if the destination path already exists, or is
  * within the source path.
 */
object copy extends Function2[Path, Path, Unit] with CopyMove{
  def apply(from: Path, to: Path) = {
    require(
      !to.startsWith(from),
      s"Can't copy a directory into itself: $to is inside $from"
    )
    def copyOne(p: Path) = {
      Files.copy(p.toNIO, (to/(p relativeTo from)).toNIO)
    }

    copyOne(from)
    if (stat(from).isDir) walk(from).map(copyOne)
  }

}

/**
 * Roughly equivalent to bash's `rm -rf`. Deletes
 * any files or folders in the target path, or
 * does nothing if there aren't any
 */
object remove extends Function1[Path, Unit]{
  def apply(target: Path): Unit = Files.delete(target.toNIO)

  object all extends Function1[Path, Unit]{
    def apply(target: Path) = {
      require(target.segments.nonEmpty, s"Cannot rm a root directory: $target")

      val nioTarget = target.toNIO
      if (Files.exists(nioTarget)) {
        if (Files.isDirectory(nioTarget)) {
          walk.iter(target, preOrder = false).foreach(remove)
        }
        Files.delete(nioTarget)
      }
    }
  }
}

/**
  * Checks if a file or folder exists at the given path.
  */
object exists extends Function1[Path, Boolean]{
  def apply(p: Path): Boolean = Files.exists(p.toNIO)
  def apply(p: Path, followLinks: Boolean = true): Boolean = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.exists(p.toNIO, opts:_*)
  }
}

/**
  * Creates a hardlink between two paths
  */
object hardlink {
  def apply(src: Path, dest: Path) = {
    Files.createLink(dest.toNIO, src.toNIO)
  }
}

/**
  * Creates a symbolic link between two paths
  */
object symlink {
  def apply(src: Path, dest: Path, perms: PermSet = null): Unit = {
    import collection.JavaConverters._
    val permArray =
      if (perms == null) Array[FileAttribute[_]]()
      else Array(PosixFilePermissions.asFileAttribute(perms.value.asJava))

    Files.createSymbolicLink(dest.toNIO, src.toNIO, permArray:_*)
  }
}


/**
  * Attempts to any symbolic links in the given path and return the canonical path.
  * Returns `None` if the path cannot be resolved (i.e. some symbolic link in the
  * given path is broken)
  */
object followLink extends Function1[Path, Option[Path]]{
  def apply(src: Path): Option[Path] = Try(Path(src.toNIO.toRealPath())).toOption
}


/**
  * Alias for `java.nio.file.Files.createTempFile` and
  * `java.io.File.deleteOnExit`. Pass in `deleteOnExit = false` if you want
  * the temp file to stick around.
  */
object temp{
  /**
    * Creates a temporary directory. You can optionally provide a `dir` to specify
    * where this file lives, a `prefix` to customize what it looks like, and a
    * [[PermSet]] to customize its filesystem permissions.
    *
    * By default, temporary directories are deleted on JVM exit. You can disable that
    * behavior by setting `deleteOnExit = false`
    */
  def dir(dir: Path = null,
          prefix: String = null,
          deleteOnExit: Boolean = true,
          perms: PermSet = null): Path = {
    import collection.JavaConverters._
    val permArray =
      if (perms == null) Array[FileAttribute[PosixFilePermission]]()
      else Array(PosixFilePermissions.asFileAttribute(perms.value.asJava))

    val nioPath = dir match{
      case null => java.nio.file.Files.createTempDirectory(prefix, permArray:_*)
      case _ => java.nio.file.Files.createTempDirectory(dir.toNIO, prefix, permArray:_*)
    }

    if (deleteOnExit) nioPath.toFile.deleteOnExit()
    Path(nioPath)
  }

  /**
    * Creates a temporary file. You can optionally provide a `dir` to specify where
    * this file lives, file-`prefix` and file-`suffix` to customize what it looks
    * like, and a [[PermSet]] to customize its filesystem permissions.
    *
    * Passing in a [[os.Source]] will initialize the contents of that file to
    * the provided data; otherwise it is created empty.
    *
    * By default, temporary files are deleted on JVM exit. You can disable that
    * behavior by setting `deleteOnExit = false`
    */
  def apply(contents: Source = null,
            dir: Path = null,
            prefix: String = null,
            suffix: String = null,
            deleteOnExit: Boolean = true,
            perms: PermSet = null): Path = {
    import collection.JavaConverters._
    val permArray =
      if (perms == null) Array[FileAttribute[PosixFilePermission]]()
      else Array(PosixFilePermissions.asFileAttribute(perms.value.asJava))

    val nioPath = dir match{
      case null => java.nio.file.Files.createTempFile(prefix, suffix, permArray:_*)
      case _ => java.nio.file.Files.createTempFile(dir.toNIO, prefix, suffix, permArray:_*)
    }

    if (contents != null) write.over(Path(nioPath), contents)
    if (deleteOnExit) nioPath.toFile.deleteOnExit()
    Path(nioPath)
  }
}