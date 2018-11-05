package test.os

import test.os.TestUtil.prep
import utest._

object ListingWalkingTests extends TestSuite {
  def tests = Tests{
    'list - {
      * - prep{ wd =>
        os.list(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")
        os.list(wd / "folder2") ==> Seq(
          wd / "folder2" / "nestedA",
          wd / "folder2" / "nestedB"
        )

        os.list(wd / "misc" / "folder-symlink") ==> Seq(
          wd / "misc" / "folder-symlink" / "one.txt"
        )
      }
      'stream - {
        * - prep{ wd =>
          os.list.stream(wd / "folder2").count() ==> 2

          // Streaming the listed files to the console
          for(line <- os.list.stream(wd / "folder2")){
            println(line)
          }
        }
      }
    }
    'walk - {
      * - prep{ wd =>
        os.walk(wd / "folder1") ==> Seq(wd / "folder1" / "one.txt")

        os.walk(wd / "folder1", includeTarget = true) ==> Seq(
          wd / "folder1",
          wd / "folder1" / "one.txt"
        )

        os.walk(wd / "folder2") ==> Seq(
          wd / "folder2" / "nestedA",
          wd / "folder2" / "nestedA" / "a.txt",
          wd / "folder2" / "nestedB",
          wd / "folder2" / "nestedB" / "b.txt"
        )

        os.walk(wd / "folder2", preOrder = false) ==> Seq(
          wd / "folder2" / "nestedA" / "a.txt",
          wd / "folder2" / "nestedA",
          wd / "folder2" / "nestedB" / "b.txt",
          wd / "folder2" / "nestedB"
        )

        os.walk(wd / "folder2", maxDepth = 1) ==> Seq(
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
      'attrs - {
        * - prep{ wd =>
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
        }
      }
      'stream - {
        * - prep{ wd =>
          os.walk.stream(wd / "folder1").count() ==> 1

          os.walk.stream(wd / "folder2").count() ==> 4

          os.walk.stream(wd / "folder2", skip = _.last == "nestedA").count() ==> 2
        }
        'attrs - {
          * - prep{ wd =>
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
