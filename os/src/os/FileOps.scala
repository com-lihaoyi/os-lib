/**
 * Basic operations that take place on files. Intended to be
 * both light enough to use from the command line as well as
 * powerful and flexible enough to use in real applications to
 * perform filesystem operations
 */
package os

import java.nio.file._
import scala.util.Try

/**
 * Makes directories up to the specified path. Equivalent
 * to `mkdir -p` in bash
 */
object makeDirs extends Function1[Path, Unit]{
  def apply(path: Path) = Files.createDirectories(path.toNIO)
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
 * Moves a file or folder from one place to another.
 *
 * Creates any necessary directories
 */
object move extends Function2[Path, Path, Unit] with Internals.Mover with CopyMove{
  def apply(from: Path, to: Path) = {
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
 * Copies a file or folder from one place to another.
 * Creates any necessary directories, and copies folders
 * recursively.
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
  def apply(target: Path) = Files.delete(target.toNIO)

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
  * Creates a hardlink between two paths
  */
object hardlink extends Function2[Path, Path, Unit]{
  def apply(src: Path, dest: Path) = {
    Files.createLink(dest.toNIO, src.toNIO)
  }
}

/**
  * Creates a symbolic link between two paths
  */
object symlink extends Function2[Path, Path, Unit]{
  def apply(src: Path, dest: Path) = {
    Files.createSymbolicLink(dest.toNIO, src.toNIO)
  }
}


/**
  * Obtain the final path to a file by resolving symlinks if any.
  */
object followLink extends Function1[Path, Option[Path]]{
  /**
    * @return Some(path) or else None if the symlink is invalid or other error.
    */
  def apply(src: Path) = Try(Path(src.toNIO.toRealPath())).toOption
}


/**
  * Alias for `java.nio.file.Files.createTempFile` and
  * `java.io.File.deleteOnExit`. Pass in `deleteOnExit = false` if you want
  * the temp file to stick around.
  */
object temp{
  /**
    * Creates a temporary directory
    */
  def dir(dir: Path = null,
          prefix: String = null,
          deleteOnExit: Boolean = true): Path = {
    val nioPath = dir match{
      case null => java.nio.file.Files.createTempDirectory(prefix)
      case _ => java.nio.file.Files.createTempDirectory(dir.toNIO, prefix)
    }
    if (deleteOnExit) nioPath.toFile.deleteOnExit()
    Path(nioPath)
  }

  /**
    * Creates a temporary file with the provided contents
    */
  def apply(contents: Source = null,
            dir: Path = null,
            prefix: String = null,
            suffix: String = null,
            deleteOnExit: Boolean = true): Path = {

    val nioPath = dir match{
      case null => java.nio.file.Files.createTempFile(prefix, suffix)
      case _ => java.nio.file.Files.createTempFile(dir.toNIO, prefix, suffix)
    }

    if (contents != null) write.over(Path(nioPath), contents)
    if (deleteOnExit) nioPath.toFile.deleteOnExit()
    Path(nioPath)
  }
}