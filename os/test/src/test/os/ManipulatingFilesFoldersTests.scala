package test.os

import test.os.TestUtil.prep
import utest._

object ManipulatingFilesFoldersTests extends TestSuite {
  def tests = Tests{
    'exists - {
      * - prep{ wd =>
        os.exists(wd / "File.txt") ==> true
        os.exists(wd / "folder1") ==> true
        os.exists(wd / "doesnt-exist") ==> false

        os.exists(wd / "misc" / "file-symlink") ==> true
        os.exists(wd / "misc" / "folder-symlink") ==> true
        os.exists(wd / "misc" / "broken-symlink") ==> false
        os.exists(wd / "misc" / "broken-symlink", followLinks = false) ==> true
      }
    }
    'move - {
      * - prep{ wd =>
        os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
        os.move(wd / "folder1" / "one.txt", wd / "folder1" / "first.txt")
        os.list(wd / "folder1") ==> Seq(wd / "folder1" / "first.txt")

        os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedA", wd / "folder2" / "nestedB")
        os.move(wd / "folder2" / "nestedA", wd / "folder2" / "nestedC")
        os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedB", wd / "folder2" / "nestedC")
      }
      'into - {
        * - prep{ wd =>

        }
      }
      'over - {
        * - prep{ wd =>

        }
      }
    }
    'copy - {
      * - prep{ wd =>

      }
      'into - {
        * - prep{ wd =>

        }
      }
      'over - {
        * - prep{ wd =>

        }
      }
    }
    'makeDir - {
      * - prep{ wd =>

      }
      'all - {
        * - prep{ wd =>

        }
      }
    }
    'remove - {
      * - prep{ wd =>

      }
      'all - {
        * - prep{ wd =>

        }
      }
    }
    'hardlink - {
      * - prep{ wd =>

      }
    }
    'symlink - {
      * - prep{ wd =>

      }
    }
    'followLink - {
      * - prep{ wd =>

      }
    }
    'temp - {
      * - prep{ wd =>

      }
      'dir - {
        * - prep{ wd =>

        }
      }
    }
  }
}
