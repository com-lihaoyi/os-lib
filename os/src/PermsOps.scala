package os

import java.nio.file.{Files, LinkOption}
import java.nio.file.attribute.{GroupPrincipal, PosixFileAttributeView, UserPrincipal}

/**
 * Get the filesystem permissions of the file/folder at the given path
 */
object perms extends Function1[Path, PermSet] {
  def apply(p: Path): PermSet = apply(p, followLinks = true)
  def apply(p: Path, followLinks: Boolean = true): PermSet = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    PermSet.fromSet(Files.getPosixFilePermissions(p.wrapped, opts: _*))
  }

  /**
   * Set the filesystem permissions of the file/folder at the given path
   *
   * Note that if you want to create a file or folder with a given set of
   * permissions, you can pass in an [[os.PermSet]] to [[os.write]]
   * or [[os.makeDir]]. That will ensure the file or folder is created
   * atomically with the given permissions, rather than being created with the
   * default set of permissions and having `os.perms.set` over-write them later
   */
  object set {
    def apply(p: Path, arg2: PermSet): Unit = {
      checker.value.onWrite(p)
      Files.setPosixFilePermissions(p.wrapped, arg2.toSet())
    }
  }

}

/**
 * Get the owner of the file/folder at the given path
 */
object owner extends Function1[Path, UserPrincipal] {
  def apply(p: Path): UserPrincipal = apply(p, followLinks = true)
  def apply(p: Path, followLinks: Boolean = true): UserPrincipal = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.getOwner(p.wrapped, opts: _*)
  }

  /**
   * Set the owner of the file/folder at the given path
   */
  object set {
    def apply(arg1: Path, arg2: UserPrincipal): Unit = {
      checker.value.onWrite(arg1)
      Files.setOwner(arg1.wrapped, arg2)
    }
    def apply(arg1: Path, arg2: String): Unit = {
      apply(
        arg1,
        arg1.wrapped.getFileSystem.getUserPrincipalLookupService.lookupPrincipalByName(arg2)
      )
    }
  }
}

/**
 * Get the owning group of the file/folder at the given path
 */
object group extends Function1[Path, GroupPrincipal] {
  def apply(p: Path): GroupPrincipal = apply(p, followLinks = true)
  def apply(p: Path, followLinks: Boolean = true): GroupPrincipal = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.getFileAttributeView(
      p.wrapped,
      classOf[PosixFileAttributeView],
      opts: _*
    ).readAttributes().group()
  }

  /**
   * Set the owning group of the file/folder at the given path
   */
  object set {
    def apply(arg1: Path, arg2: GroupPrincipal): Unit = {
      checker.value.onWrite(arg1)
      Files.getFileAttributeView(
        arg1.wrapped,
        classOf[PosixFileAttributeView],
        LinkOption.NOFOLLOW_LINKS
      ).setGroup(arg2)
    }
    def apply(arg1: Path, arg2: String): Unit = {
      apply(
        arg1,
        arg1.wrapped.getFileSystem.getUserPrincipalLookupService.lookupPrincipalByGroupName(arg2)
      )
    }
  }
}
