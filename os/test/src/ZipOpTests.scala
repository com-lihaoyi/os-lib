package test.os

import test.os.TestUtil.prep
import utest._

import scala.collection.immutable.Seq

object ZipOpTests extends TestSuite {

  def tests = Tests {

    test("zipAndUnzipFolder") {

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

    test("zipByExcludingCertainFiles") {

      test - prep { wd =>

        val amxFile = "File.amx"
        os.copy(wd / "File.txt", wd / amxFile)

        // Zipping files and folders in a new zip file
        val zipFileName = "zipByExcludingCertainFiles.zip"
        val zipFile1: os.Path = os.zip(
          destination = wd / zipFileName,
          listOfPaths = List(
            wd / "File.txt",
            wd / amxFile,
            wd / "Multi Line.txt"
          ),
          excludePatterns = List(".*\\.txt")
        )

        // Unzip file to check for contents
        val outputZipFilePath = os.unzip(zipFile1, destination = Some(wd / "zipByExcludingCertainFiles"))
        val paths = os.walk(outputZipFilePath)
        assert(paths.length == 1)
        assert(paths.contains(outputZipFilePath / amxFile))
      }
    }

    test("zipByIncludingCertainFiles") {

      test - prep { wd =>

        val amxFile = "File.amx"
        os.copy(wd / "File.txt", wd / amxFile)

        // Zipping files and folders in a new zip file
        val zipFileName = "zipByIncludingCertainFiles.zip"
        val zipFile1: os.Path = os.zip(
          destination = wd / zipFileName,
          listOfPaths = List(
            wd / "File.txt",
            wd / amxFile,
            wd / "Multi Line.txt"
          ),
          includePatterns = List(".*\\.amx")
        )

        // Unzip file to check for contents
        val outputZipFilePath = os.unzip(zipFile1, destination = Some(wd / "zipByIncludingCertainFiles"))
        val paths = os.walk(outputZipFilePath)
        assert(paths.length == 1)
        assert(paths.contains(outputZipFilePath / amxFile))
      }
    }

//    test("zipByDeletingCertainFiles") {
//
//      test - prep { wd =>
//
//        val amxFile = "File.amx"
//        os.copy(wd / "File.txt", wd / amxFile)
//
//        // Zipping files and folders in a new zip file
//        val zipFileName = "zipByDeletingCertainFiles.zip"
//        val zipFile1: os.Path = os.zip(
//          destination = wd / zipFileName,
//          listOfPaths = List(
//            wd / "File.txt",
//            wd / amxFile,
//            wd / "Multi Line.txt"
//          )
//        )
//
//        os.zip(
//          destination = zipFile1,
//          deletePatterns = List(amxFile)
//        )
//
//        // Unzip file to check for contents
//        val outputZipFilePath = os.unzip(zipFile1, destination = Some(wd / "zipByDeletingCertainFiles"))
//        val paths = os.walk(outputZipFilePath)
//        assert(paths.length == 2)
//        assert(paths.contains(outputZipFilePath / "File.txt"))
//        assert(paths.contains(outputZipFilePath / "Multi Line.txt"))
//      }
//    }
  }

}
