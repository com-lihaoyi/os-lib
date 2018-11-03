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
      }
      'attrs - {
        * - prep{ wd =>
          os.walk.attrs(wd / "folder1")
        }
      }
      'stream - {
        * - prep{ wd =>

        }
        'attrs - {
          * - prep{ wd =>

          }
        }
      }
    }
  }
}
