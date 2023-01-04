package os

import java.nio.file.attribute.{FileAttribute, PosixFilePermissions}
import scala.util.{Using, Try}

/**
 * Create temporary files and directories. [[withTempFile]] and [[withTempDir]] 
 * are convenience methods that handle the most common case. They delete the temp
 * file/dir immediately after the given function completed - even if the given
 * function threw an exception. 
 * 
 * {{{
 * withTempFile { file =>
 *   os.write(file, "some content")
 * }
 * }}} 
 * 
 * [[os.temp()]] and [[os.temp.dir()]] are aliases for
 * `java.nio.file.Files.createTemp[File|Directory]` and `java.io.File.deleteOnExit`.
 * Pass in `deleteOnExit = false` if you want the temp file to stick around.
 */
object temp {

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
  def apply(
      contents: Source = null,
      dir: Path = null,
      prefix: String = null,
      suffix: String = null,
      deleteOnExit: Boolean = true,
      perms: PermSet = null
  ): TempPath = {
    import collection.JavaConverters._
    val permArray: Array[FileAttribute[_]] =
      if (perms == null) Array.empty
      else Array(PosixFilePermissions.asFileAttribute(perms.toSet()))

    val nioPath = dir match {
      case null => java.nio.file.Files.createTempFile(prefix, suffix, permArray: _*)
      case _ => java.nio.file.Files.createTempFile(dir.wrapped, prefix, suffix, permArray: _*)
    }

    if (contents != null) write.over(Path(nioPath), contents)
    if (deleteOnExit) nioPath.toFile.deleteOnExit()
    TempPath(nioPath)
  }

  /**
   * Creates a temporary directory. You can optionally provide a `dir` to specify
   * where this file lives, a `prefix` to customize what it looks like, and a
   * [[PermSet]] to customize its filesystem permissions.
   *
   * By default, temporary directories are deleted on JVM exit. You can disable that
   * behavior by setting `deleteOnExit = false`
   */
  def dir(
      dir: Path = null,
      prefix: String = null,
      deleteOnExit: Boolean = true,
      perms: PermSet = null
  ): TempPath = {
    val permArray: Array[FileAttribute[_]] =
      if (perms == null) Array.empty
      else Array(PosixFilePermissions.asFileAttribute(perms.toSet()))

    val nioPath = dir match {
      case null => java.nio.file.Files.createTempDirectory(prefix, permArray: _*)
      case _ => java.nio.file.Files.createTempDirectory(dir.wrapped, prefix, permArray: _*)
    }

    if (deleteOnExit) nioPath.toFile.deleteOnExit()
    TempPath(nioPath)
  }

  /**
    * Convenience method that creates a temporary file and automatically deletes it
    * after the given function completed - even if the function throws an exception. 
    * 
    * {{{
    * withTempFile { file =>
    *   os.write(file, "some content")
    * }
    * }}} 
    */
  def withTempFile[A](fun: Path => A): Try[A] =
    Using(os.temp(
      deleteOnExit = false // TempFile.close() deletes it, no need to register with JVM
    ))(fun)

  /**
    * Convenience method that creates a temporary directory and automatically deletes it
    * after the given function completed - even if the function throws an exception. 
    * 
    * {{{
    * withTempDir { file =>
    *   val file = dir / "somefile"
    *   os.write(file, "some content")
    * }
    * }}} 
    */
  def withTempDir[A](fun: Path => A): Try[A] =
    Using(os.temp.dir(
      deleteOnExit = false // TempFile.close() deletes it, no need to register with JVM
    ))(fun)

}
