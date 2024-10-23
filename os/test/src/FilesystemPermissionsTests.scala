package test.os

import test.os.TestUtil._
import utest._

object FilesystemPermissionsTests extends TestSuite {
  def tests = Tests {
    // restricted directory
    val rd = os.pwd / "os/test/resources/restricted"

    test("perms") {
      test - prep { wd =>
        if (Unix()) {
          os.perms.set(wd / "File.txt", "rwxrwxrwx")
          os.perms(wd / "File.txt").toString() ==> "rwxrwxrwx"
          os.perms(wd / "File.txt").toInt() ==> Integer.parseInt("777", 8)

          os.perms.set(wd / "File.txt", Integer.parseInt("755", 8))
          os.perms(wd / "File.txt").toString() ==> "rwxr-xr-x"

          os.perms.set(wd / "File.txt", "r-xr-xr-x")
          os.perms.set(wd / "File.txt", Integer.parseInt("555", 8))
        }
      }
      test("checker") - prepChecker { wd =>
        if (Unix()) {
          val before = os.perms(rd / "File.txt")
          intercept[WriteDenied] {
            os.perms.set(rd / "File.txt", "rwxrwxrwx")
          }
          os.perms(rd / "File.txt") ==> before

          os.perms.set(wd / "File.txt", "rwxrwxrwx")
          os.perms(wd / "File.txt").toString() ==> "rwxrwxrwx"
          os.perms(wd / "File.txt").toInt() ==> Integer.parseInt("777", 8)

          os.perms.set(wd / "File.txt", Integer.parseInt("755", 8))
          os.perms(wd / "File.txt").toString() ==> "rwxr-xr-x"

          os.perms.set(wd / "File.txt", "r-xr-xr-x")
          os.perms.set(wd / "File.txt", Integer.parseInt("555", 8))
        }
      }
    }
    test("owner") {
      test - prep { wd =>
        if (Unix()) {
          // Only works as root :(
          if (false) {
            val originalOwner = os.owner(wd / "File.txt")

            os.owner.set(wd / "File.txt", "nobody")
            os.owner(wd / "File.txt").getName ==> "nobody"

            os.owner.set(wd / "File.txt", originalOwner)
          }
        }
      }
      test("checker") - prepChecker { wd =>
        if (Unix()) {
          // Only works as root :(
          if (false) {
            intercept[WriteDenied] {
              os.owner.set(rd / "File.txt", "nobody")
            }

            val originalOwner = os.owner(wd / "File.txt")

            os.owner.set(wd / "File.txt", "nobody")
            os.owner(wd / "File.txt").getName ==> "nobody"

            os.owner.set(wd / "File.txt", originalOwner)
          }
        }
      }
    }
    test("group") {
      test - prep { wd =>
        if (Unix()) {
          // Only works as root :(
          if (false) {
            val originalGroup = os.group(wd / "File.txt")

            os.group.set(wd / "File.txt", "nobody")
            os.group(wd / "File.txt").getName ==> "nobody"

            os.group.set(wd / "File.txt", originalGroup)
          }
        }
      }
      test("checker") - prepChecker { wd =>
        if (Unix()) {
          // Only works as root :(
          if (false) {
            intercept[WriteDenied] {
              os.group.set(rd / "File.txt", "nobody")
            }

            val originalGroup = os.group(wd / "File.txt")

            os.group.set(wd / "File.txt", "nobody")
            os.group(wd / "File.txt").getName ==> "nobody"

            os.group.set(wd / "File.txt", originalGroup)
          }
        }
      }
    }
  }
}
