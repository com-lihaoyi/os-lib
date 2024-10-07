package os

import java.nio.file.attribute.PosixFilePermission

private[os] object PlatformShims {
  // Scala-Native does not support reading posix file permissions
  // https://github.com/scala-native/scala-native/issues/4067
  def readPermissions(file: java.io.File): Option[java.util.Set[PosixFilePermission]] = None
}
