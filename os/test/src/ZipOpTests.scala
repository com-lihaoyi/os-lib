package test.os

import os.zip
import test.os.TestUtil.prep
import utest._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, PrintStream, PrintWriter, StringWriter}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.immutable.Seq
import java.nio.file.{Files, Paths}
import java.nio.file.attribute.FileTime
import java.time.{Instant, ZoneId}
import java.time.temporal.{ChronoUnit, TemporalUnit}
import scala.collection.JavaConverters._

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
        val outputZipFilePath =
          os.unzip(zipFile1, destination = Some(wd / "zipByExcludingCertainFiles"))
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
        val outputZipFilePath =
          os.unzip(zipFile1, destination = Some(wd / "zipByIncludingCertainFiles"))
        val paths = os.walk(outputZipFilePath)
        assert(paths.length == 1)
        assert(paths.contains(outputZipFilePath / amxFile))
      }
    }

    test("zipByDeletingCertainFiles") {

      test - prep { wd =>
        val amxFile = "File.amx"
        os.copy(wd / "File.txt", wd / amxFile)

        // Zipping files and folders in a new zip file
        val zipFileName = "zipByDeletingCertainFiles.zip"
        val zipFile1: os.Path = os.zip(
          destination = wd / zipFileName,
          listOfPaths = List(
            wd / "File.txt",
            wd / amxFile,
            wd / "Multi Line.txt"
          )
        )

        os.zip(
          destination = zipFile1,
          deletePatterns = List(amxFile)
        )

        // Unzip file to check for contents
        val outputZipFilePath =
          os.unzip(zipFile1, destination = Some(wd / "zipByDeletingCertainFiles"))
        val paths = os.walk(outputZipFilePath)
        assert(paths.length == 2)
        assert(paths.contains(outputZipFilePath / "File.txt"))
        assert(paths.contains(outputZipFilePath / "Multi Line.txt"))
      }
    }

    test("listContentsOfZipFileWithoutExtracting") {

      test - prep { wd =>
        // Zipping files and folders in a new zip file
        val zipFileName = "listContentsOfZipFileWithoutExtracting.zip"
        val zipFile: os.Path = os.zip(
          destination = wd / zipFileName,
          listOfPaths = List(
            wd / "File.txt",
            wd / "folder1"
          )
        )
        val originalOut = System.out
        val outputStream = new ByteArrayOutputStream()
        System.setOut(new PrintStream(outputStream))

        // Unzip file to a destination folder
        val unzippedFolder = os.unzip(
          source = wd / zipFileName,
          listOnly = true
        )

        // Then
        val capturedOutput: Array[String] = outputStream.toString.split("\n")
        assert(capturedOutput.length <= 2)

        // Restore the original output stream
        System.setOut(originalOut)
      }
    }

    test("unzipAllExceptExcludingCertainFiles") {

      test - prep { wd =>
        val amxFile = "File.amx"
        os.copy(wd / "File.txt", wd / amxFile)

        val zipFileName = "unzipAllExceptExcludingCertainFiles.zip"
        val zipFile: os.Path = os.zip(
          destination = wd / zipFileName,
          listOfPaths = List(
            wd / "File.txt",
            wd / amxFile,
            wd / "folder1"
          )
        )

        // Unzip file to a destination folder
        val unzippedFolder = os.unzip(
          source = wd / zipFileName,
          destination = Some(wd / "unzipAllExceptExcludingCertainFiles"),
          excludePatterns = List(amxFile)
        )

        val paths = os.walk(unzippedFolder)
        assert(paths.length == 3)
        assert(paths.contains(unzippedFolder / "File.txt"))
        assert(paths.contains(unzippedFolder / "folder1" / "one.txt"))
      }
    }

    test("zipStreamFunction") {
      test - prep { wd =>
        val streamOutput = new ByteArrayOutputStream()
        val zipFileName = "zipStreamFunction.zip"

        // Create a stream to zip a folder
        val writable = zip.stream(
          source = wd / "File.txt",
          destination = Some(wd / zipFileName),
          excludePatterns = List(),
          includePatterns = List(),
          deletePatterns = List()
        )

        // Write the zipped data to the stream
        writable.writeBytesTo(streamOutput)

        val unzippedFolder = os.unzip(
          source = wd / zipFileName,
          destination = Some(wd / "zipStreamFunction")
        )
        val paths = os.walk(unzippedFolder)
        assert(paths.length == 1)
        assert(paths.contains(unzippedFolder / "File.txt"))
      }
    }

    test("unzipStreamFunction") {
      test - prep { wd =>
        // Step 1: Create an in-memory ZIP file as a stream
        val zipStreamOutput = new ByteArrayOutputStream()
        val zipOutputStream = new ZipOutputStream(zipStreamOutput)

        // Step 2: Add some files to the ZIP
        val file1Name = "file1.txt"
        val file2Name = "nested/folder/file2.txt"

        // Add first file
        zipOutputStream.putNextEntry(new ZipEntry(file1Name))
        zipOutputStream.write("Content of file1".getBytes)
        zipOutputStream.closeEntry()

        // Add second file inside a nested folder
        zipOutputStream.putNextEntry(new ZipEntry(file2Name))
        zipOutputStream.write("Content of file2".getBytes)
        zipOutputStream.closeEntry()

        // Close the ZIP output stream
        zipOutputStream.close()

        // Step 3: Prepare the destination folder for unzipping
        val unzippedFolder = wd / "unzipped-stream-folder"
        val readableZipStream = geny.Readable.ByteArrayReadable(zipStreamOutput.toByteArray)

        // Unzipping the stream to the destination folder
        os.unzip.stream(
          source = readableZipStream,
          destination = unzippedFolder
        )

        // Step 5: Verify the unzipped files and contents
        val paths = os.walk(unzippedFolder)
        assert(paths.contains(unzippedFolder / file1Name))
        assert(paths.contains(unzippedFolder / "nested" / "folder" / "file2.txt"))

        // Check the contents of the files
        val file1Content = os.read(unzippedFolder / file1Name)
        val file2Content = os.read(unzippedFolder / "nested" / "folder" / "file2.txt")

        assert(file1Content == "Content of file1")
        assert(file2Content == "Content of file2")
      }
    }

    test("zipAndUnzipPreserveMtimes") {
      test - prep { wd =>
        // Create a file and set its modification time
        val testFile = wd / "FileWithMtime.txt"
        os.write(testFile, "Test content")

        // Use basic System.currentTimeMillis() for modification time
        val originalMtime = System.currentTimeMillis() - (1 * 60 * 1000) // 1 minute ago
        val path = Paths.get(testFile.toString)
        Files.setLastModifiedTime(path, FileTime.fromMillis(originalMtime))

        // Zipping the file with preserveMtimes = true
        val zipFileName = "zipWithMtimePreservation.zip"
        val zipFile: os.Path = os.zip(
          destination = wd / zipFileName,
          listOfPaths = List(testFile),
          preserveMtimes = true
        )

        val existingZipFile = new ZipFile(zipFile.toNIO.toFile)
        val actualMTime = existingZipFile.entries().asScala.toList.head.getTime

        // Compare the original and actual modification times (in minutes)
        assert((originalMtime / (1000 * 60)) == (actualMTime / (1000 * 60)))
      }
    }
  }
}
