package test.os

import test.os.TestUtil.prep
import utest._

import scala.collection.immutable.Seq

object ZipOpTests extends TestSuite {

  def tests = Tests {

    test("zipOps") {

      test - prep { wd =>
        // Zipping files and folders in a new zip file
        val zipFile1: os.Path = os.zipIn(
          destination = wd / "zip-file1-test.zip",
          listOfPaths = List(
            wd / "File.txt",
            wd / "folder1"
          )
        )
//
//        // Adding files and folders to an existing zip file
//        val zipFile2: os.Path = os.zipIn(
//          destination = wd / "zip-file1-test.zip",
//          listOfPaths = List(
//            wd / "folder2",
//            wd / "Multi Line.txt"
//          )
//        )

        // Unzip
//        print("Test Done")
      }
    }
  }

}
