package test.os

import test.os.TestUtil.prep
import utest._

object ManipulatingFilesFoldersTests extends TestSuite {
  def tests = Tests{
    test("exists"){
      test - prep{ wd =>
        os.exists(wd / "File.txt") ==> true
        os.exists(wd / "folder1") ==> true
        os.exists(wd / "doesnt-exist") ==> false

        os.exists(wd / "misc" / "file-symlink") ==> true
        os.exists(wd / "misc" / "folder-symlink") ==> true
        os.exists(wd / "misc" / "broken-symlink") ==> false
        os.exists(wd / "misc" / "broken-symlink", followLinks = false) ==> true
      }
    }
    test("move"){
      test - prep{ wd =>
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
      test("matching"){
        test - prep{ wd =>
          import os.{GlobSyntax, /}
          os.walk(wd / "folder2").toSet ==> Set(
            wd / "folder2" / "nestedA",
            wd / "folder2" / "nestedA" / "a.txt",
            wd / "folder2" / "nestedB",
            wd / "folder2" / "nestedB" / "b.txt"
          )

          os.walk(wd/"folder2").collect(os.move.matching{case p/g"$x.txt" => p/g"$x.data"})

          os.walk(wd / "folder2").toSet ==> Set(
            wd / "folder2" / "nestedA",
            wd / "folder2" / "nestedA" / "a.data",
            wd / "folder2" / "nestedB",
            wd / "folder2" / "nestedB" / "b.data"
          )
        }
      }
      test("into"){
        test - prep{ wd =>
          os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
          os.move.into(wd / "File.txt", wd / "folder1")
          os.list(wd / "folder1") ==> Seq(wd / "folder1" / "File.txt", wd / "folder1" / "one.txt")
        }
      }
      test("over"){
        test - prep{ wd =>
          os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedA", wd / "folder2" / "nestedB")
          os.move.over(wd / "folder1", wd / "folder2")
          os.list(wd / "folder2") ==> Seq(wd / "folder2" / "one.txt")
        }
      }
    }
    test("copy"){
      test - prep{ wd =>
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
      test("into"){
        test - prep{ wd =>
          os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
          os.copy.into(wd / "File.txt", wd / "folder1")
          os.list(wd / "folder1") ==> Seq(wd / "folder1" / "File.txt", wd / "folder1" / "one.txt")
        }
      }
      test("over"){
        test - prep{ wd =>
          os.list(wd / "folder2") ==> Seq(wd / "folder2" / "nestedA", wd / "folder2" / "nestedB")
          os.copy.over(wd / "folder1", wd / "folder2")
          os.list(wd / "folder2") ==> Seq(wd / "folder2" / "one.txt")
        }
      }
      test("symlinks") {
        val src = os.temp.dir(deleteOnExit = true)
        
        os.makeDir(src / "t0")
        os.write(src / "t0" / "file", "hello")
        os.symlink(src / "t1", os.rel / "t0")

        val dest = os.temp.dir(deleteOnExit = true)

        os.copy(src / "t0", dest / "t0", followLinks = false, replaceExisting = false)
        os.copy(src / "t1", dest / "t1", followLinks = false, replaceExisting = false)

        val src_list = os.walk(src, includeTarget = false, followLinks = false)
          .map(_ relativeTo src)
          .sorted
        val dest_list = os.walk(dest, includeTarget = false, followLinks = false)
          .map(_ relativeTo dest)
          .sorted

        assert(dest_list == src_list)

        src_list.foreach { r =>
          val src_path = src / r
          val dest_path = dest / r

          if (os.isFile(src_path, followLinks = false)) {
            assert(os.isFile(dest_path, followLinks = false))
            assert(os.read(src_path) == os.read(dest_path))
          } else if (os.isLink(src_path)) {
            assert(os.isLink(dest_path))
            assert(os.readLink(src_path) == os.readLink(dest_path))
          } else if (os.isDir(src_path, followLinks = false)) {
            assert(os.isDir(dest_path, followLinks = false))
            val s = os.list(src_path, sort = true).map(_ relativeTo src).toList
            val d = os.list(dest_path, sort = true).map(_ relativeTo dest).toList
            assert(d == s)
          } else {
            assert(false)
          }

        }
      }
    }
    test("makeDir"){
      test - prep{ wd =>
        os.exists(wd / "new_folder") ==> false
        os.makeDir(wd / "new_folder")
        os.exists(wd / "new_folder") ==> true
      }
      test("all"){
        test - prep{ wd =>
          os.exists(wd / "new_folder") ==> false
          os.makeDir.all(wd / "new_folder" / "inner" / "deep")
          os.exists(wd / "new_folder" / "inner" / "deep") ==> true
        }
      }
    }
    test("remove"){
      test - prep{ wd =>
        os.exists(wd / "File.txt") ==> true
        os.remove(wd / "File.txt")
        os.exists(wd / "File.txt") ==> false

        os.exists(wd / "folder1" / "one.txt") ==> true
        os.remove(wd / "folder1" / "one.txt")
        os.remove(wd / "folder1")
        os.exists(wd / "folder1" / "one.txt") ==> false
        os.exists(wd / "folder1") ==> false
      }
      test("link"){
        test - prep{ wd =>
          os.remove(wd / "misc" / "file-symlink")
          os.exists(wd / "misc" / "file-symlink", followLinks = false) ==> false
          os.exists(wd / "File.txt", followLinks = false) ==> true

          os.remove(wd / "misc" / "folder-symlink")
          os.exists(wd / "misc" / "folder-symlink", followLinks = false) ==> false
          os.exists(wd / "folder1", followLinks = false) ==> true
          os.exists(wd / "folder1" / "one.txt", followLinks = false) ==> true

          os.remove(wd / "misc" / "broken-symlink")
          os.exists(wd / "misc" / "broken-symlink", followLinks = false) ==> false
        }
      }
      test("all"){
        test - prep{ wd =>
          os.exists(wd / "folder1" / "one.txt") ==> true
          os.remove.all(wd / "folder1")
          os.exists(wd / "folder1" / "one.txt") ==> false
          os.exists(wd / "folder1") ==> false
        }
        test("link"){
          test - prep{ wd =>
            os.remove.all(wd / "misc" / "file-symlink")
            os.exists(wd / "misc" / "file-symlink", followLinks = false) ==> false
            os.exists(wd / "File.txt", followLinks = false) ==> true

            os.remove.all(wd / "misc" / "folder-symlink")
            os.exists(wd / "misc" / "folder-symlink", followLinks = false) ==> false
            os.exists(wd / "folder1", followLinks = false) ==> true
            os.exists(wd / "folder1" / "one.txt", followLinks = false) ==> true

            os.remove.all(wd / "misc" / "broken-symlink")
            os.exists(wd / "misc" / "broken-symlink", followLinks = false) ==> false
          }
        }
      }
    }
    test("hardlink"){
      test - prep{ wd =>
        os.hardlink(wd / "Linked.txt", wd / "File.txt")
        os.exists(wd / "Linked.txt")
        os.read(wd / "Linked.txt") ==> "I am cow"
        os.isLink(wd / "Linked.txt") ==> false
      }
    }
    test("symlink"){
      test - prep{ wd =>
        os.symlink(wd / "Linked.txt", wd / "File.txt")
        os.exists(wd / "Linked.txt")
        os.read(wd / "Linked.txt") ==> "I am cow"
        os.isLink(wd / "Linked.txt") ==> true

        os.symlink(wd / "Linked2.txt", os.rel / "File.txt")
        os.exists(wd / "Linked2.txt")
        os.read(wd / "Linked2.txt") ==> "I am cow"
        os.isLink(wd / "Linked2.txt") ==> true
      }
    }
    test("followLink"){
      test - prep{ wd =>
        os.followLink(wd / "misc" / "file-symlink") ==> Some(wd / "File.txt")
        os.followLink(wd / "misc" / "folder-symlink") ==> Some(wd / "folder1")
        os.followLink(wd / "misc" / "broken-symlink") ==> None
      }
    }
    test("readLink"){
      test - prep{ wd =>  if(Unix()){
        os.readLink(wd / "misc" / "file-symlink") ==> os.up / "File.txt"
        os.readLink(wd / "misc" / "folder-symlink") ==> os.up / "folder1"
        os.readLink(wd / "misc" / "broken-symlink") ==> os.rel / "broken"
        os.readLink(wd / "misc" / "broken-abs-symlink") ==> os.root / "doesnt" / "exist"

        os.readLink.absolute(wd / "misc" / "file-symlink") ==> wd / "File.txt"
        os.readLink.absolute(wd / "misc" / "folder-symlink") ==> wd / "folder1"
        os.readLink.absolute(wd / "misc" / "broken-symlink") ==> wd / "misc" / "broken"
        os.readLink.absolute(wd / "misc" / "broken-abs-symlink") ==> os.root / "doesnt" / "exist"
      }}
    }
    test("temp"){
      test - prep{ wd =>
        val tempOne = os.temp("default content")
        os.read(tempOne) ==> "default content"
        os.write.over(tempOne, "Hello")
        os.read(tempOne) ==> "Hello"
      }
      test("dir"){
        test - prep{ wd =>
          val tempDir = os.temp.dir()
          os.list(tempDir) ==> Nil
          os.write(tempDir / "file", "Hello")
          os.list(tempDir) ==> Seq(tempDir / "file")
        }
      }
    }
  }
}
