/**
 * Basic operations that take place on files. Intended to be
 * both light enough to use from the command line as well as
 * powerful and flexible enough to use in real applications to
 * perform filesystem operations
 */
package os

import java.nio.file
import java.nio.file.{Path => _, _}
import java.nio.file.attribute.{FileAttribute, PosixFilePermission, PosixFilePermissions}

import scala.util.Try
import scala.annotation.unroll

/**
 * Create a single directory at the specified path. Optionally takes in a
 * [[PermSet]] to specify the filesystem permissions of the created
 * directory.
 *
 * Errors out if the directory already exists, or if the parent directory of the
 * specified path does not exist. To automatically create enclosing directories and
 * ignore the destination if it already exists, using [[os.makeDir.all]]
 */
object makeDir extends Function1[Path, Unit] {
  def apply(path: Path): Unit = Files.createDirectory(path.wrapped)
  def apply(path: Path, perms: PermSet): Unit = {
    Files.createDirectory(
      path.wrapped,
      PosixFilePermissions.asFileAttribute(perms.toSet())
    )
  }

  /**
   * Similar to [[os.makeDir]], but automatically creates any necessary
   * enclosing directories if they do not exist, and does not raise an error if the
   * destination path already containts a directory
   */
  object all extends Function1[Path, Unit] {
    def apply(path: Path): Unit = apply(path, null, true)
    def apply(path: Path, perms: PermSet = null, acceptLinkedDirectory: Boolean = true): Unit = {
      // We special case calling makeDir.all on a symlink to a directory;
      // normally createDirectories blows up noisily, when really what most
      // people would want is for it to succeed since there is a (linked)
      // directory right there
      if (os.isDir(path) && os.isLink(path) && acceptLinkedDirectory) () // do nothing
      else if (perms == null) Files.createDirectories(path.wrapped)
      else {
        Files.createDirectories(
          path.wrapped,
          PosixFilePermissions.asFileAttribute(perms.toSet())
        )
      }
    }
  }
}

/**
 * Moves a file or folder from one path to another. Errors out if the destination
 * path already exists, or is within the source path.
 */
object move {
  def matching(
      replaceExisting: Boolean = false,
      atomicMove: Boolean = false,
      createFolders: Boolean = false
  )(partialFunction: PartialFunction[Path, Path]): PartialFunction[Path, Unit] = {
    new PartialFunction[Path, Unit] {
      def isDefinedAt(x: Path) = partialFunction.isDefinedAt(x)
      def apply(from: Path) = {
        val dest = partialFunction(from)
        makeDir.all(dest / up)
        os.move(from, dest, replaceExisting, atomicMove, createFolders)
      }
    }

  }
  def matching(partialFunction: PartialFunction[Path, Path]): PartialFunction[Path, Unit] = {
    matching()(partialFunction)
  }
  def apply(
      from: Path,
      to: Path,
      replaceExisting: Boolean = false,
      atomicMove: Boolean = false,
      createFolders: Boolean = false
  ): Unit = {
    if (createFolders) makeDir.all(to / up)
    val opts1 =
      if (replaceExisting) Array[CopyOption](StandardCopyOption.REPLACE_EXISTING)
      else Array[CopyOption]()
    val opts2 =
      if (atomicMove) Array[CopyOption](StandardCopyOption.ATOMIC_MOVE)
      else Array[CopyOption]()
    require(
      !to.startsWith(from),
      s"Can't move a directory into itself: $to is inside $from"
    )
    java.nio.file.Files.move(from.wrapped, to.wrapped, opts1 ++ opts2: _*)
  }

  /**
   * Move a file into a particular folder, rather
   * than into a particular path
   */
  object into {
    def apply(
        from: Path,
        to: Path,
        replaceExisting: Boolean = false,
        atomicMove: Boolean = false,
        createFolders: Boolean = false
    ): Unit = {
      move.apply(from, to / from.last, replaceExisting, atomicMove, createFolders)
    }
  }

  /**
   * Move a file into a particular folder, rather
   * than into a particular path
   */
  object over {
    def apply(
        from: Path,
        to: Path,
        replaceExisting: Boolean = false,
        atomicMove: Boolean = false,
        createFolders: Boolean = false
    ): Unit = {
      os.remove.all(to)
      move.apply(from, to, replaceExisting, atomicMove, createFolders)
    }
  }
}

/**
 * Copy a file or folder from one path to another. Recursively copies folders with
 * all their contents. Errors out if the destination path already exists, or is
 * within the source path.
 */
object copy {
  def matching(
      followLinks: Boolean = true,
      replaceExisting: Boolean = false,
      copyAttributes: Boolean = false,
      createFolders: Boolean = false,
      mergeFolders: Boolean = false
  )(partialFunction: PartialFunction[Path, Path]): PartialFunction[Path, Unit] = {
    new PartialFunction[Path, Unit] {
      def isDefinedAt(x: Path) = partialFunction.isDefinedAt(x)
      def apply(from: Path) = {
        val dest = partialFunction(from)
        makeDir.all(dest / up)
        os.copy(
          from,
          dest,
          followLinks,
          replaceExisting,
          copyAttributes,
          createFolders,
          mergeFolders
        )
      }
    }

  }
  def matching(partialFunction: PartialFunction[Path, Path]): PartialFunction[Path, Unit] = {
    matching()(partialFunction)
  }

  def apply(
      from: Path,
      to: Path,
      followLinks: Boolean = true,
      replaceExisting: Boolean = false,
      copyAttributes: Boolean = false,
      createFolders: Boolean = false,
      @unroll mergeFolders: Boolean = false
  ): Unit = {
    if (createFolders) makeDir.all(to / up)
    val opts1 =
      if (followLinks) Array[CopyOption]()
      else Array[CopyOption](LinkOption.NOFOLLOW_LINKS)
    val opts2 =
      if (replaceExisting) Array[CopyOption](StandardCopyOption.REPLACE_EXISTING)
      else Array[CopyOption]()
    val opts3 =
      if (copyAttributes) Array[CopyOption](StandardCopyOption.COPY_ATTRIBUTES)
      else Array[CopyOption]()
    require(
      !to.startsWith(from),
      s"Can't copy a directory into itself: $to is inside $from"
    )

    def copyOne(p: Path): file.Path = {
      val target = to / p.relativeTo(from)
      if (mergeFolders && isDir(p, followLinks) && isDir(target, followLinks)) {
        // nothing to do
        target.wrapped
      } else {
        Files.copy(p.wrapped, target.wrapped, opts1 ++ opts2 ++ opts3: _*)
      }
    }

    copyOne(from)
    if (stat(from, followLinks = followLinks).isDir) walk(from).map(copyOne)
  }

  /**
   * Copy a file into a particular folder, rather
   * than into a particular path
   */
  object into {
    def apply(
        from: Path,
        to: Path,
        followLinks: Boolean = true,
        replaceExisting: Boolean = false,
        copyAttributes: Boolean = false,
        createFolders: Boolean = false,
        @unroll mergeFolders: Boolean = false
    ): Unit = {
      os.copy(
        from,
        to / from.last,
        followLinks,
        replaceExisting,
        copyAttributes,
        createFolders,
        mergeFolders
      )
    }
  }

  /**
   * Copy a file into a particular path
   */
  object over {
    def apply(
        from: Path,
        to: Path,
        followLinks: Boolean = true,
        replaceExisting: Boolean = false,
        copyAttributes: Boolean = false,
        createFolders: Boolean = false
    ): Unit = {
      os.remove.all(to)
      os.copy(
        from = from,
        to = to,
        followLinks = followLinks,
        replaceExisting = replaceExisting,
        copyAttributes = copyAttributes,
        createFolders = createFolders,
        mergeFolders = false
      )
    }
  }
}

/**
 * Roughly equivalent to bash's `rm -rf`. Deletes
 * any files or folders in the target path, or
 * does nothing if there aren't any
 */
object remove extends Function1[Path, Boolean] {
  def apply(target: Path): Boolean = apply(target, false)
  def apply(target: Path, checkExists: Boolean = false): Boolean = {
    if (checkExists) {
      Files.delete(target.wrapped)
      true
    } else {
      Files.deleteIfExists(target.wrapped)
    }
  }

  object all extends Function1[Path, Unit] {
    def apply(target: Path) = {
      require(target.segmentCount != 0, s"Cannot remove a root directory: $target")

      val nioTarget = target.wrapped
      if (Files.exists(nioTarget, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isDirectory(nioTarget, LinkOption.NOFOLLOW_LINKS)) {
          walk.stream(target, preOrder = false).foreach(remove(_))
        }
        Files.delete(nioTarget)
      }
    }
  }
}

/**
 * Checks if a file or folder exists at the given path.
 */
object exists extends Function1[Path, Boolean] {
  def apply(p: Path): Boolean = Files.exists(p.wrapped)
  def apply(p: Path, followLinks: Boolean = true): Boolean = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.exists(p.wrapped, opts: _*)
  }
}

/**
 * Creates a hardlink between two paths
 */
object hardlink {
  def apply(link: Path, dest: Path) = {
    Files.createLink(link.wrapped, dest.wrapped)
  }
}

/**
 * Creates a symbolic link between two paths
 */
object symlink {
  def apply(link: Path, dest: FilePath, perms: PermSet = null): Unit = {
    val permArray: Array[FileAttribute[_]] =
      if (perms == null) Array[FileAttribute[_]]()
      else Array(PosixFilePermissions.asFileAttribute(perms.toSet()))

    Files.createSymbolicLink(
      link.toNIO,
      dest match {
        // Special case empty relative paths, because for some reason `createSymbolicLink`
        // doesn't like it when the path is "" (most other Files.* functions are fine)
        case p: RelPath if p.segments.isEmpty && p.ups == 0 => java.nio.file.Paths.get(".")
        case p: SubPath if p.segments.isEmpty => java.nio.file.Paths.get(".")
        case _ => dest.toNIO
      },
      permArray: _*
    )
  }
}

/**
 * Attempts to any symbolic links in the given path and return the canonical path.
 * Returns `None` if the path cannot be resolved (i.e. some symbolic link in the
 * given path is broken)
 */
object followLink extends Function1[Path, Option[Path]] {
  def apply(src: Path): Option[Path] = Try(Path(src.wrapped.toRealPath())).toOption
}

/**
 * Reads the destination that the given symbolic link is pointed to
 */
object readLink extends Function1[Path, os.FilePath] {
  def apply(src: Path): FilePath = os.FilePath(Files.readSymbolicLink(src.toNIO))
  def absolute(src: Path): os.Path = os.Path(Files.readSymbolicLink(src.toNIO), src / up)
}
