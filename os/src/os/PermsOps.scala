package os

import java.nio.file.{Files, LinkOption}
import java.nio.file.attribute.{GroupPrincipal, PosixFileAttributeView, UserPrincipal}

/**
  * Get the filesystem permissions of the file/folder at the given path
  */
object getPerms extends Function1[Path, PermSet]{
  def apply(p: Path): PermSet = apply(p, followLinks = true)
  def apply(p: Path, followLinks: Boolean = true): PermSet = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    import collection.JavaConverters._
    new PermSet(Files.getPosixFilePermissions(p.toNIO, opts:_*).asScala.toSet)
  }
}

/**
  * Set the filesystem permissions of the file/folder at the given path
  */
object setPerms {
  def apply(p: Path, arg2: PermSet): Unit = {
    import collection.JavaConverters._
    Files.setPosixFilePermissions(p.toNIO, arg2.value.asJava)
  }
}

/**
  * Get the owner of the file/folder at the given path
  */
object getOwner extends Function1[Path, UserPrincipal] {
  def apply(p: Path): UserPrincipal = apply(p, followLinks = true)
  def apply(p: Path, followLinks: Boolean = true): UserPrincipal = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.getOwner(p.toNIO, opts:_*)
  }
}

/**
  * Set the owner of the file/folder at the given path
  */
object setOwner {
  def apply(arg1: Path, arg2: UserPrincipal): Unit = Files.setOwner(arg1.toNIO, arg2)
  def apply(arg1: Path, arg2: String): Unit = {
    apply(
      arg1,
      arg1.root.getFileSystem.getUserPrincipalLookupService.lookupPrincipalByName(arg2)
    )
  }
}

/**
  * Get the owning group of the file/folder at the given path
  */
object getGroup extends Function1[Path, GroupPrincipal] {
  def apply(p: Path): GroupPrincipal = apply(p, followLinks = true)
  def apply(p: Path, followLinks: Boolean = true): GroupPrincipal = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.getFileAttributeView(
      p.toNIO,
      classOf[PosixFileAttributeView],
      LinkOption.NOFOLLOW_LINKS
    ).readAttributes().group()
  }
}

/**
  * Set the owning group of the file/folder at the given path
  */
object setGroup {
  def apply(arg1: Path, arg2: GroupPrincipal): Unit = {
    Files.getFileAttributeView(
      arg1.toNIO,
      classOf[PosixFileAttributeView],
      LinkOption.NOFOLLOW_LINKS
    ).setGroup(arg2)
  }
  def apply(arg1: Path, arg2: String): Unit = {
    apply(
      arg1,
      arg1.root.getFileSystem.getUserPrincipalLookupService.lookupPrincipalByGroupName(arg2)
    )
  }
}