package test.os
import TestUtil.prep
import utest._

import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Paths}
import java.util.zip.ZipFile
import scala.collection.JavaConverters._

object ZipOpJvmTests extends TestSuite {

  def tests = Tests {

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

    test("zipAndUnzipDontPreserveMtimes") - prep { wd =>

      val testFile = wd / "FileWithMtime.txt"
      os.write.over(testFile, "Test content")

      val mtime1 = os.mtime(testFile)

      val zipFile1 = os.zip(
        dest = wd / "zipWithoutMtimes1.zip",
        sources = List(testFile),
        preserveMtimes = false
      )
      Thread.sleep(1000) // Sleep a bit to make sure the mtime has time to change
      os.write.over(testFile, "Test content")

      val mtime2 = os.mtime(testFile)

      val zipFile2 = os.zip(
        dest = wd / "zipWithoutMtimes2.zip",
        sources = List(testFile),
        preserveMtimes = false
      )

      // Even though the mtimes of the two included files are different, the two
      // final zip files end up being byte-for-byte the same because the mtimes get wiped
      assert(mtime1 != mtime2)
      assert(
        java.util.Arrays.equals(
          os.read.bytes(zipFile1),
          os.read.bytes(zipFile2)
        )
      )
    }
  }
}
