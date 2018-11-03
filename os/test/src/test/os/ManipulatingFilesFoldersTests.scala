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

        os.read(wd / "File.txt") ==> "I am cow"
        os.move(wd / "Multi Line.txt", wd / "File.txt", replaceExisting = true)
        os.read(wd / "File.txt") ==>
          """I am cow
            |Hear me moo
            |I weigh twice as much as you
            |And I look good on the barbecue""".stripMargin
      }
      'into - {
        * - prep{ wd =>
          os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
          os.move.into(wd / "File.txt", wd / "folder1")
          os.list(wd / "folder1") ==> Seq(wd / "folder1" / "File.txt", wd / "folder1" / "one.txt")
        }
      }
      'over - {
        * - prep{ wd =>
          os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedA", wd / "folder2" / "nestedB")
          os.move.over(wd / "folder1", wd / "folder2")
          os.list(wd / "folder2") ==> Seq(wd / "folder2" / "one.txt")
        }
      }
    }
    'copy - {
      * - prep{ wd =>
        os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
        os.copy(wd / "folder1" / "one.txt", wd / "folder1" / "first.txt")
        os.list(wd / "folder1") ==> Seq(wd / "folder1" / "first.txt", wd / "folder1" / "one.txt")

        os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedA", wd / "folder2" / "nestedB")
        os.copy(wd / "folder2" / "nestedA", wd / "folder2" / "nestedC")
        os.list(wd / "folder2") ==> Seq(
          wd / "folder2" / "nestedA",
          wd / "folder2" / "nestedB",
          wd / "folder2" / "nestedC"
        )

        os.read(wd / "File.txt") ==> "I am cow"
        os.copy(wd / "Multi Line.txt", wd / "File.txt", replaceExisting = true)
        os.read(wd / "File.txt") ==>
          """I am cow
            |Hear me moo
            |I weigh twice as much as you
            |And I look good on the barbecue""".stripMargin
      }
      'into - {
        * - prep{ wd =>
          os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
          os.copy.into(wd / "File.txt", wd / "folder1")
          os.list(wd / "folder1") ==> Seq(wd / "folder1" / "File.txt", wd / "folder1" / "one.txt")
        }
      }
      'over - {
        * - prep{ wd =>
          os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedA", wd / "folder2" / "nestedB")
          os.copy.over(wd / "folder1", wd / "folder2")
          os.list(wd / "folder2") ==> Seq(wd / "folder2" / "one.txt")
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
