package os

import java.nio.file.{Files, LinkOption}
import java.nio.file.attribute.{GroupPrincipal, PosixFileAttributeView, UserPrincipal}


object getPerms {
  def apply(p: Path): PermSet = apply(p, followLinks = true)
  def apply(p: Path, followLinks: Boolean = true): PermSet = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    import collection.JavaConverters._
    new PermSet(Files.getPosixFilePermissions(p.toNIO, opts:_*).asScala.toSet)
  }
}

object setPerms {
  def apply(p: Path, arg2: PermSet) = {
    import collection.JavaConverters._
    Files.setPosixFilePermissions(p.toNIO, arg2.value.asJava)
  }
}

object getOwner {
  def apply(p: Path): UserPrincipal = apply(p, followLinks = true)
  def apply(p: Path, followLinks: Boolean = true): UserPrincipal = {
    val opts = if (followLinks) Array[LinkOption]() else Array(LinkOption.NOFOLLOW_LINKS)
    Files.getOwner(p.toNIO, opts:_*)
  }
}

object setOwner {
  def apply(arg1: Path, arg2: UserPrincipal): Unit = Files.setOwner(arg1.toNIO, arg2)
  def apply(arg1: Path, arg2: String): Unit = {
    apply(
      arg1,
      arg1.root.getFileSystem.getUserPrincipalLookupService.lookupPrincipalByName(arg2)
    )
  }
}

object getGroup {
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