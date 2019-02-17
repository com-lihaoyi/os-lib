package test.os

import test.os.TestUtil.prep
import utest._

object FilesystemMetadataTests extends TestSuite {

  // on unix it is 81 bytes, win adds 3 bytes (3 \r characters)
  private val multilineSizes = Set[Long](81, 84)

  def tests = Tests{
    'stat - {
      * - prep{ wd =>
        os.stat(wd / "File.txt").size ==> 8
        assert(multilineSizes contains os.stat(wd / "Multi Line.txt").size)
        os.stat(wd / "folder1").fileType ==> os.FileType.Dir
      }
      'full - {
        * - prep{ wd =>
          os.stat.full(wd / "File.txt").size ==> 8
          assert(multilineSizes contains os.stat.full(wd / "Multi Line.txt").size)
          os.stat.full(wd / "folder1").fileType ==> os.FileType.Dir
        }
      }
    }
    'isFile - {
      * - prep{ wd =>
        os.isFile(wd / "File.txt") ==> true
        os.isFile(wd / "folder1") ==> false

        os.isFile(wd / "misc" / "file-symlink") ==> true
        os.isFile(wd / "misc" / "folder-symlink") ==> false
        os.isFile(wd / "misc" / "file-symlink", followLinks = false) ==> false
      }
    }
    'isDir - {
      * - prep{ wd =>
        os.isDir(wd / "File.txt") ==> false
        os.isDir(wd / "folder1") ==> true

        os.isDir(wd / "misc" / "file-symlink") ==> false
        os.isDir(wd / "misc" / "folder-symlink") ==> true
        os.isDir(wd / "misc" / "folder-symlink", followLinks = false) ==> false
      }
    }
    'isLink- {
      * - prep{ wd =>
        os.isLink(wd / "misc" / "file-symlink") ==> true
        os.isLink(wd / "misc" / "folder-symlink") ==> true
        os.isLink(wd / "folder1") ==> false
      }
    }
    'size  {
      * - prep{ wd =>
        os.size(wd / "File.txt") ==> 8
        assert(multilineSizes contains os.size(wd / "Multi Line.txt"))
      }
    }
    'mtime - {
      * - prep{ wd =>
        os.mtime.set(wd / "File.txt", 0)
        os.mtime(wd / "File.txt") ==> 0

        os.mtime.set(wd / "File.txt", 90000)
        os.mtime(wd / "File.txt") ==> 90000
        os.mtime(wd / "misc" / "file-symlink") ==> 90000

        os.mtime.set(wd / "misc" / "file-symlink", 70000)
        os.mtime(wd / "File.txt") ==> 70000
        os.mtime(wd / "misc" / "file-symlink") ==> 70000
        assert(os.mtime(wd / "misc" / "file-symlink", followLinks = false) != 40000)

      }
    }
  }
}
