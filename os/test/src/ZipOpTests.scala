package test.os

import test.os.TestUtil.prep
import utest._

import scala.collection.immutable.Seq

object ZipOpTests extends TestSuite {

  def tests = Tests {

    test("zipOps") {

      test - prep { wd =>
        // Zipping files and folders in a new zip file
        val zipFileName = "zip-file-test.zip"
        val zipFile1: os.Path = os.zip(
          destination = wd / zipFileName,
          listOfPaths = List(
            wd / "File.txt",
            wd / "folder1"
          )
        )

        // Adding files and folders to an existing zip file
        val zipFile2: os.Path = os.zip(
          destination = wd / zipFileName,
          listOfPaths = List(
            wd / "folder2",
            wd / "Multi Line.txt"
          ),
          appendToExisting = true
        )

        // Unzip file to a destination folder
        val unzippedFolder = os.unzip(
          source = wd / zipFileName,
          destination = Some(wd / "unzipped folder")
        )

        val paths = os.walk(unzippedFolder)
        assert(paths.length == 9)
        assert(paths.contains(unzippedFolder / "File.txt"))
        assert(paths.contains(unzippedFolder / "Multi Line.txt"))
        assert(paths.contains(unzippedFolder / "folder1" / "one.txt"))
        assert(paths.contains(unzippedFolder / "folder2" / "nestedB" / "b.txt"))
      }
    }
  }

}
