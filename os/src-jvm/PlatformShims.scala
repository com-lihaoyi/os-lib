package os

import java.nio.file.Files
import java.nio.file.attribute.{PosixFileAttributeView, PosixFilePermission}

private[os] object PlatformShims{
  def readPermissions(file: java.nio.file.Path): Option[java.util.Set[PosixFilePermission]] = {
    val posixView = Files.getFileAttributeView(file, classOf[PosixFileAttributeView])
    if (posixView != null) {
      val attrs = posixView.readAttributes()
      val perms = attrs.permissions()
      Some(perms)
    } else None
  }
}
