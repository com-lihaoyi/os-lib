package test.os
import TestUtil.prep
import utest._

import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Paths}
import java.util.zip.ZipFile
import scala.collection.JavaConverters._

object ZipOpJvmTests extends TestSuite {

  def tests = Tests {

    test("zipAndUnzipFolder") - prep { wd =>
      // Zipping files and folders in a new zip file
      val zipFileName = "zip-file-test.zip"
      val zipFile1: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          wd / "File.txt",
          wd / "folder1"
        )
      )
      // Adding files and folders to an existing zip file
      os.zip(
        dest = zipFile1,
        sources = Seq(
          wd / "folder2",
          wd / "Multi Line.txt"
        )
      )

      // Unzip file to a destination folder
      val unzippedFolder = os.unzip(
        source = wd / zipFileName,
        dest = wd / "unzipped folder"
      )

      val paths = os.walk(unzippedFolder)
      val expected = Seq(
        // Files get included in the zip root using their name
        wd / "unzipped folder/File.txt",
        wd / "unzipped folder/Multi Line.txt",
        // Folder contents get included relative to the source root
        wd / "unzipped folder/nestedA",
        wd / "unzipped folder/nestedB",
        wd / "unzipped folder/one.txt",
        wd / "unzipped folder/nestedA/a.txt",
        wd / "unzipped folder/nestedB/b.txt"
      )
      assert(paths.sorted == expected)
    }

    test("zipAndUnzipPreserveMtimes") - prep { wd =>
      // Create a file and set its modification time
      val testFile = wd / "FileWithMtime.txt"
      os.write(testFile, "Test content")

      // Use basic System.currentTimeMillis() for modification time
      val originalMtime = System.currentTimeMillis() - (1 * 60 * 1000) // 1 minute ago
      val path = Paths.get(testFile.toString)
      Files.setLastModifiedTime(path, FileTime.fromMillis(originalMtime))

      // Zipping the file with preserveMtimes = true
      val zipFile = os.zip(
        dest = wd / "zipWithMtimePreservation.zip",
        sources = List(testFile),
        preserveMtimes = true
      )

      val existingZipFile = new ZipFile(zipFile.toNIO.toFile)
      val actualMTime = existingZipFile.entries().asScala.toList.head.getTime

      // Compare the original and actual modification times (in minutes)
      assert((originalMtime / (1000 * 60)) == (actualMTime / (1000 * 60)))
    }

    def zipAndUnzipDontPreserveMtimes(wd: os.Path, exerciseAppend: Boolean) = {

      val testFile = wd / "FileWithMtime.txt"
      os.write.over(testFile, "Test content")
      val testFile2 = wd / "FileWithMtime2.txt"

      val mtime1 = os.mtime(testFile)

      val zipFile1 = os.zip(
        dest = wd / "zipWithoutMtimes1.zip",
        sources = List(testFile),
        preserveMtimes = false
      )

      if (exerciseAppend) {

        os.write.over(testFile2, "Test content2")
        os.zip(
          dest = wd / "zipWithoutMtimes1.zip",
          sources = List(testFile2),
          preserveMtimes = false
        )
      }

      // Sleep a bit to make sure the mtime has time to change, since zip files may
      // have a very coarse granulity of up to two seconds
      // https://stackoverflow.com/questions/64048499/zipfile-lib-weird-behaviour-with-seconds-in-modified-time
      Thread.sleep(5000)
      os.write.over(testFile, "Test content")

      val mtime2 = os.mtime(testFile)

      val zipFile2 = os.zip(
        dest = wd / "zipWithoutMtimes2.zip",
        sources = List(testFile),
        preserveMtimes = false
      )

      if (exerciseAppend) {
        os.write.over(testFile2, "Test content2")
        os.zip(
          dest = wd / "zipWithoutMtimes2.zip",
          sources = List(testFile2),
          preserveMtimes = false
        )
      }

      // Even though the mtimes of the two included files are different, the two
      // final zip files end up being byte-for-byte the same because the mtimes get wiped
      assert(mtime1 != mtime2)
      assert(java.util.Arrays.equals(os.read.bytes(zipFile1), os.read.bytes(zipFile2)))
    }

    test("zipAndUnzipDontPreserveMtimes") {
      test("noAppend") - prep { wd => zipAndUnzipDontPreserveMtimes(wd, false) }
      test("append") - prep { wd => zipAndUnzipDontPreserveMtimes(wd, true) }
    }

    test("zipAndUnzipPreservePermissions") - prep { wd =>
      if (Unix()) {
        // Zipping files and folders in a new zip file
        val zipFileName = "zip-file-test.zip"
        os.perms.set(wd / "File.txt", "rwxr-xr-x")
        os.perms.set(wd / "folder1/nestedA", "rw-rw-rw-")
        val zipFile1: os.Path = os.zip(
            dest = wd / zipFileName,
            sources = Seq(
                wd / "File.txt",
                wd / "folder1"
            )
        )
        // Adding files and folders to an existing zip file
        os.zip(
            dest = zipFile1,
            sources = Seq(
                wd / "folder2",
                wd / "Multi Line.txt"
            )
        )

        // Unzip file to a destination folder
        val unzippedFolder = os.unzip(
            source = wd / zipFileName,
            dest = wd / "unzipped folder"
        )

        val paths = os.walk(unzippedFolder)
        val expected = Seq(
            // Files get included in the zip root using their name
            wd / "unzipped folder/File.txt",
            wd / "unzipped folder/Multi Line.txt",
            // Folder contents get included relative to the source root
            wd / "unzipped folder/nestedA",
            wd / "unzipped folder/nestedB",
            wd / "unzipped folder/one.txt",
            wd / "unzipped folder/nestedA/a.txt",
            wd / "unzipped folder/nestedB/b.txt"
        )
        assert(paths.sorted == expected)

        os.perms(wd / "unzipped folder/File.txt") ==> "rwxr-xr-x"
        os.perms(wd / "unzipped folder/nestedA") ==> "rw-rw-rw-"
      }
    }

    test("deletePatterns") - prep { wd =>
      val amxFile = "File.amx"
      os.copy(wd / "File.txt", wd / amxFile)

      // Zipping files and folders in a new zip file
      val zipFileName = "zipByDeletingCertainFiles.zip"
      val zipFile1: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = List(
          wd / "File.txt",
          wd / amxFile,
          wd / "Multi Line.txt"
        )
      )

      os.zip(
        dest = zipFile1,
        deletePatterns = List(amxFile.r)
      )

      // Unzip file to check for contents
      val outputZipFilePath = os.unzip(
        zipFile1,
        dest = wd / "zipByDeletingCertainFiles"
      )
      val paths = os.walk(wd / "zipByDeletingCertainFiles").sorted
      val expected = Seq(
        outputZipFilePath / "File.txt",
        outputZipFilePath / "Multi Line.txt"
      )

      assert(paths == expected)
    }

    test("open") - prep { wd =>
      val zipFile = os.zip.open(wd / "zip-test.zip")
      try {
        os.copy(wd / "File.txt", zipFile / "File.txt")
        os.copy(wd / "folder1", zipFile / "folder1")
        os.copy(wd / "folder2", zipFile / "folder2")
      } finally zipFile.close()

      val zipFile2 = os.zip.open(wd / "zip-test.zip")
      try {
        os.list(zipFile2) ==> Vector(
          zipFile2 / "File.txt",
          zipFile2 / "folder1",
          zipFile2 / "folder2"
        )
        os.remove.all(zipFile2 / "folder2")
        os.remove(zipFile2 / "File.txt")
      } finally zipFile2.close()

      val zipFile3 = os.zip.open(wd / "zip-test.zip")
      try os.list(zipFile3) ==> Vector(zipFile3 / "folder1")
      finally zipFile3.close()

    }
  }
}
