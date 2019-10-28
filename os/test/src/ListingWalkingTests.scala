package test.os

import test.os.TestUtil.prep
import utest._

object ListingWalkingTests extends TestSuite {
  def tests = Tests{
    test("list"){
      test - prep{ wd =>
        os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
        os.list(wd / "folder2") ==> Seq(
          wd / "folder2" / "nestedA",
          wd / "folder2" / "nestedB"
        )

        os.list(wd / "misc" / "folder-symlink") ==> Seq(
          wd / "misc" / "folder-symlink" / "one.txt"
        )
      }
      test("stream"){
        test - prep{ wd =>
          os.list.stream(wd / "folder2").count() ==> 2

          // Streaming the listed files to the console
          for(line <- os.list.stream(wd / "folder2")){
            println(line)
          }
        }
      }
    }
    test("walk"){
      test - prep{ wd =>
        os.walk(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")

        os.walk(wd / "folder1", includeTarget = true) ==> Seq(
          wd / "folder1",
          wd / "folder1" / "one.txt"
        )

        os.walk(wd / "folder2").toSet ==> Set(
          wd / "folder2" / "nestedA",
          wd / "folder2" / "nestedA" / "a.txt",
          wd / "folder2" / "nestedB",
          wd / "folder2" / "nestedB" / "b.txt"
        )

        os.walk(wd / "folder2", preOrder = false).toSet ==> Set(
          wd / "folder2" / "nestedA" / "a.txt",
          wd / "folder2" / "nestedA",
          wd / "folder2" / "nestedB" / "b.txt",
          wd / "folder2" / "nestedB"
        )

        os.walk(wd / "folder2", maxDepth = 1).toSet ==> Set(
          wd / "folder2" / "nestedA",
          wd / "folder2" / "nestedB"
        )

        os.walk(wd / "folder2", skip = _.last == "nestedA") ==> Seq(
          wd / "folder2" / "nestedB",
          wd / "folder2" / "nestedB" / "b.txt"
        )

        os.walk(wd / "misc" / "folder-symlink") ==> Seq(
          wd / "misc" / "folder-symlink" / "one.txt"
        )
      }
      test("attrs"){
        test - prep{ wd => if(Unix()){
          val filesSortedBySize = os.walk.attrs(wd / "misc", followLinks = true)
            .sortBy{case (p, attrs) => attrs.size}
            .collect{case (p, attrs) if attrs.isFile => p}

          filesSortedBySize ==> Seq(
            wd / "misc" / "echo",
            wd / "misc" / "file-symlink",
            wd / "misc" / "echo_with_wd",
            wd / "misc" / "folder-symlink" / "one.txt",
            wd / "misc" / "binary.png"
          )
        }}
      }
      test("stream"){
        test - prep{ wd =>
          os.walk.stream(wd / "folder1").count() ==> 1

          os.walk.stream(wd / "folder2").count() ==> 4

          os.walk.stream(wd / "folder2", skip = _.last == "nestedA").count() ==> 2
        }
        test("attrs"){
          test - prep{ wd =>
            def totalFileSizes(p: os.Path) = os.walk.stream.attrs(p)
              .collect{case (p, attrs) if attrs.isFile => attrs.size}
              .sum

            totalFileSizes(wd / "folder1") ==> 22
            totalFileSizes(wd / "folder2") ==> 40
          }
        }
      }
    }
  }
}
