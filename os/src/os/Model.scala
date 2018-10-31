package os

import java.nio.file.{LinkOption, Paths, Files}
import java.nio.file.attribute._
import collection.JavaConverters._

import scala.util.Try

/**
  * Simple enum with the possible filesystem objects a path can resolve to
  */
sealed trait FileType
object FileType{
  case object File extends FileType
  case object Dir extends FileType
  case object SymLink extends FileType
  case object Other extends FileType
}
object PermSet{
  implicit def constructFromSet(arg: Set[PosixFilePermission]): PermSet = {
    new PermSet(arg)
  }
  implicit def constructFromString(arg: String): PermSet = {
    require(
      arg.length == 9,
      "Invalid permissions string: must be length 9, not " + arg.length
    )
    import PosixFilePermission._
    val perms = new java.util.HashSet[PosixFilePermission]()
    def add(i: Int, expected: Char, perm: PosixFilePermission) = {
      if(arg(i) == expected) perms.add(perm)
      else if (arg(i) != '-') {
        throw new Exception(
          "Invalid permissions string: unknown character [" + arg(i) + "] " +
            "at index " + i + ". Must be [-] or [" + expected + "]."
        )
      }
    }
    add(0, 'r', OWNER_READ)
    add(1, 'w', OWNER_WRITE)
    add(2, 'x', OWNER_EXECUTE)
    add(3, 'r', GROUP_READ)
    add(4, 'w', GROUP_WRITE)
    add(5, 'x', GROUP_EXECUTE)
    add(6, 'r', OTHERS_READ)
    add(7, 'w', OTHERS_WRITE)
    add(8, 'x', OTHERS_EXECUTE)
    new PermSet(perms.asScala.toSet)
  }
  implicit def constructFromInt(arg: Int): PermSet = {
    import PosixFilePermission._
    val perms = new java.util.HashSet[PosixFilePermission]()
    def add(i: Int, perm: PosixFilePermission) = {
      if((arg & (0x100 >> i)) != 0) perms.add(perm)
    }
    add(0, OWNER_READ)
    add(1, OWNER_WRITE)
    add(2, OWNER_EXECUTE)
    add(3, GROUP_READ)
    add(4, GROUP_WRITE)
    add(5, GROUP_EXECUTE)
    add(6, OTHERS_READ)
    add(7, OTHERS_WRITE)
    add(8, OTHERS_EXECUTE)
    new PermSet(perms.asScala.toSet)
  }
}

/**
  * A set of permissions
  */
class PermSet(val value: Set[PosixFilePermission]) {
  def contains(elem: PosixFilePermission) = value.contains(elem)
  def +(elem: PosixFilePermission) = new PermSet(value + elem)
  def -(elem: PosixFilePermission) = new PermSet(value - elem)
  def iterator = value.iterator
  def toInt(): Int = {
    var total = 0
    import PosixFilePermission._
    val perms = new java.util.HashSet[PosixFilePermission]()
    def add(i: Int, perm: PosixFilePermission) = {
      if (value.contains(perm)) total += (0x100 >> i)
    }
    add(0, OWNER_READ)
    add(1, OWNER_WRITE)
    add(2, OWNER_EXECUTE)
    add(3, GROUP_READ)
    add(4, GROUP_WRITE)
    add(5, GROUP_EXECUTE)
    add(6, OTHERS_READ)
    add(7, OTHERS_WRITE)
    add(8, OTHERS_EXECUTE)
    total
  }
  override def toString() = {
    PosixFilePermissions.toString(value.asJava)
  }
}

