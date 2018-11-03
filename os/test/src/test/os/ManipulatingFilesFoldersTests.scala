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
      'matching - {
        * - prep{ wd =>
          import os.{GlobSyntax, /}
          os.walk(wd / "folder2") ==> Seq(
            wd / "folder2" / "nestedA",
            wd / "folder2" / "nestedA" / "a.txt",
            wd / "folder2" / "nestedB",
            wd / "folder2" / "nestedB" / "b.txt"
          )

          os.walk(wd/'folder2).collect(os.move.matching{case p/g"$x.txt" => p/g"$x.data"})

          os.walk(wd / "folder2") ==> Seq(
            wd / "folder2" / "nestedA",
            wd / "folder2" / "nestedA" / "a.data",
            wd / "folder2" / "nestedB",
            wd / "folder2" / "nestedB" / "b.data"
          )
        }
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
        os.exists(wd / "new_folder") ==> false
        os.makeDir(wd / "new_folder")
        os.exists(wd / "new_folder") ==> true
      }
      'all - {
        * - prep{ wd =>
          os.exists(wd / "new_folder") ==> false
          os.makeDir.all(wd / "new_folder" / "inner" / "deep")
          os.exists(wd / "new_folder" / "inner" / "deep") ==> true
        }
      }
    }
    'remove - {
      * - prep{ wd =>
        os.exists(wd / "File.txt") ==> true
        os.remove(wd / "File.txt")
        os.exists(wd / "File.txt") ==> false

        os.exists(wd / "folder1" / "one.txt") ==> true
        os.remove(wd / "folder1" / "one.txt")
        os.remove(wd / "folder1")
        os.exists(wd / "folder1" / "one.txt") ==> false
        os.exists(wd / "folder1") ==> false
      }
      'all - {
        * - prep{ wd =>
          os.exists(wd / "folder1" / "one.txt") ==> true
          os.remove.all(wd / "folder1")
          os.exists(wd / "folder1" / "one.txt") ==> false
          os.exists(wd / "folder1") ==> false
        }
      }
    }
    'hardlink - {
      * - prep{ wd =>
        os.hardlink(wd / "File.txt", wd / "Linked.txt")
        os.exists(wd / "Linked.txt")
        os.read(wd / "Linked.txt") ==> "I am cow"
        os.isLink(wd / "Linked.txt") ==> false
      }
    }
    'symlink - {
      * - prep{ wd =>
        os.symlink(wd / "File.txt", wd / "Linked.txt")
        os.exists(wd / "Linked.txt")
        os.read(wd / "Linked.txt") ==> "I am cow"
        os.isLink(wd / "Linked.txt") ==> true
      }
    }
    'followLink - {
      * - prep{ wd =>
        os.followLink(wd / "misc" / "file-symlink") ==> Some(wd / "File.txt")
        os.followLink(wd / "misc" / "folder-symlink") ==> Some(wd / "folder1")
        os.followLink(wd / "misc" / "broken-symlink") ==> None
      }
    }
    'temp - {
      * - prep{ wd =>
        val tempOne = os.temp("default content")
        os.read(tempOne) ==> "default content"
        os.write.over(tempOne, "Hello")
        os.read(tempOne) ==> "Hello"
      }
      'dir - {
        * - prep{ wd =>
          val tempDir = os.temp.dir()
          os.list(tempDir) ==> Nil
          os.write(tempDir / "file", "Hello")
          os.list(tempDir) ==> Seq(tempDir / "file")
        }
      }
    }
  }
}
