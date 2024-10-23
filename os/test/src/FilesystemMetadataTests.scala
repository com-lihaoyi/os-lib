package test.os

import test.os.TestUtil._
import utest._

object FilesystemMetadataTests extends TestSuite {

  // on unix it is 81 bytes, win adds 3 bytes (3 \r characters)
  private val multilineSizes = Set[Long](81, 84)

  def tests = Tests {
    // restricted directory
    val rd = os.pwd / "os/test/resources/restricted"

    test("stat") {
      test - prep { wd =>
        os.stat(wd / "File.txt").size ==> 8
        assert(multilineSizes contains os.stat(wd / "Multi Line.txt").size)
        os.stat(wd / "folder1").fileType ==> os.FileType.Dir
      }
//      test("full"){
//        test - prep{ wd =>
//          os.stat.full(wd / "File.txt").size ==> 8
//          assert(multilineSizes contains os.stat.full(wd / "Multi Line.txt").size)
//          os.stat.full(wd / "folder1").fileType ==> os.FileType.Dir
//        }
//      }
    }
    test("isFile") {
      test - prep { wd =>
        os.isFile(wd / "File.txt") ==> true
        os.isFile(wd / "folder1") ==> false

        os.isFile(wd / "misc/file-symlink") ==> true
        os.isFile(wd / "misc/folder-symlink") ==> false
        os.isFile(wd / "misc/file-symlink", followLinks = false) ==> false
      }
    }
    test("isDir") {
      test - prep { wd =>
        os.isDir(wd / "File.txt") ==> false
        os.isDir(wd / "folder1") ==> true

        os.isDir(wd / "misc/file-symlink") ==> false
        os.isDir(wd / "misc/folder-symlink") ==> true
        os.isDir(wd / "misc/folder-symlink", followLinks = false) ==> false
      }
    }
    test("isLink") {
      test - prep { wd =>
        os.isLink(wd / "misc/file-symlink") ==> true
        os.isLink(wd / "misc/folder-symlink") ==> true
        os.isLink(wd / "folder1") ==> false
      }
    }
    test("size") {
      test - prep { wd =>
        os.size(wd / "File.txt") ==> 8
        assert(multilineSizes contains os.size(wd / "Multi Line.txt"))
      }
    }
    test("mtime") {
      test - prep { wd =>
        os.mtime.set(wd / "File.txt", 0)
        os.mtime(wd / "File.txt") ==> 0

        os.mtime.set(wd / "File.txt", 90000)
        os.mtime(wd / "File.txt") ==> 90000
        os.mtime(wd / "misc/file-symlink") ==> 90000

        os.mtime.set(wd / "misc/file-symlink", 70000)
        os.mtime(wd / "File.txt") ==> 70000
        os.mtime(wd / "misc/file-symlink") ==> 70000
        assert(os.mtime(wd / "misc/file-symlink", followLinks = false) != 40000)
      }
      test("checker") - prepChecker { wd =>
        val before = os.mtime(rd / "File.txt")
        intercept[WriteDenied] {
          os.mtime.set(rd / "File.txt", 0)
        }
        os.mtime(rd / "File.txt") ==> before

        os.mtime.set(wd / "File.txt", 0)
        os.mtime(wd / "File.txt") ==> 0

        os.mtime.set(wd / "File.txt", 90000)
        os.mtime(wd / "File.txt") ==> 90000
        os.mtime(wd / "misc/file-symlink") ==> 90000

        os.mtime.set(wd / "misc/file-symlink", 70000)
        os.mtime(wd / "File.txt") ==> 70000
        os.mtime(wd / "misc/file-symlink") ==> 70000
        assert(os.mtime(wd / "misc/file-symlink", followLinks = false) != 40000)
      }
    }
  }
}
