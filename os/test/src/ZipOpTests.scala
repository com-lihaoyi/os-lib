package test.os

import os.zip
import test.os.TestUtil.prep
import utest._

import java.io.{ByteArrayOutputStream, PrintStream}
import java.util.zip.{ZipEntry, ZipOutputStream}

object ZipOpTests extends TestSuite {

  def tests = Tests {

    test("zipAndUnzipFolder") - prep { wd =>
      // Zipping files and folders in a new zip file
      val zipFileName = "zip-file-test.zip"
      val zipFile1: os.Path = os.zip(
        destination = wd / zipFileName,
        sourcePaths = List(
          wd / "File.txt",
          wd / "folder1"
        )
      )

      // Adding files and folders to an existing zip file
      val zipFile2: os.Path = os.zip(
        destination = wd / zipFileName,
        sourcePaths = List(
          wd / "folder2",
          wd / "Multi Line.txt"
        ),
        appendToExisting = true
      )

      // Unzip file to a destination folder
      val unzippedFolder = os.unzip(
        source = wd / zipFileName,
        destination = wd / "unzipped folder"
      )

      val paths = os.walk(unzippedFolder)
      assert(paths.length == 9)
      assert(paths.contains(unzippedFolder / "File.txt"))
      assert(paths.contains(unzippedFolder / "Multi Line.txt"))
      assert(paths.contains(unzippedFolder / "folder1" / "one.txt"))
      assert(paths.contains(unzippedFolder / "folder2" / "nestedB" / "b.txt"))
    }


    test("zipByExcludingCertainFiles") - prep { wd =>
      val amxFile = "File.amx"
      os.copy(wd / "File.txt", wd / amxFile)

      // Zipping files and folders in a new zip file
      val zipFileName = "zipByExcludingCertainFiles.zip"
      val zipFile1: os.Path = os.zip(
        destination = wd / zipFileName,
        sourcePaths = List(
          wd / "File.txt",
          wd / amxFile,
          wd / "Multi Line.txt"
        ),
        excludePatterns = List(".*\\.txt")
      )

      // Unzip file to check for contents
      val outputZipFilePath =
        os.unzip(zipFile1, destination = wd / "zipByExcludingCertainFiles")
      val paths = os.walk(outputZipFilePath)
      assert(paths.length == 1)
      assert(paths.contains(outputZipFilePath / amxFile))
    }

    test("zipByIncludingCertainFiles") - prep { wd =>
      val amxFile = "File.amx"
      os.copy(wd / "File.txt", wd / amxFile)

      // Zipping files and folders in a new zip file
      val zipFileName = "zipByIncludingCertainFiles.zip"
      val zipFile1: os.Path = os.zip(
        destination = wd / zipFileName,
        sourcePaths = List(
          wd / "File.txt",
          wd / amxFile,
          wd / "Multi Line.txt"
        ),
        includePatterns = List(".*\\.amx")
      )

      // Unzip file to check for contents
      val outputZipFilePath =
        os.unzip(zipFile1, destination = wd / "zipByIncludingCertainFiles")
      val paths = os.walk(outputZipFilePath)
      assert(paths.length == 1)
      assert(paths.contains(outputZipFilePath / amxFile))
    }

    test("listContentsOfZipFileWithoutExtracting") - prep { wd =>
      // Zipping files and folders in a new zip file
      val zipFileName = "listContentsOfZipFileWithoutExtracting.zip"
      val zipFile: os.Path = os.zip(
        destination = wd / zipFileName,
        sourcePaths = List(
          wd / "File.txt",
          wd / "folder1"
        )
      )
      val originalOut = System.out
      val outputStream = new ByteArrayOutputStream()
      System.setOut(new PrintStream(outputStream))

      // Unzip file to a destination folder
      val listedContents = os.unzip.list(source = wd / zipFileName).toSeq

      val expected = Seq(os.sub / "File.txt", os.sub / "folder1/one.txt")
      assert(listedContents == expected)
    }

    test("unzipAllExceptExcludingCertainFiles") - prep { wd =>
      val amxFile = "File.amx"
      os.copy(wd / "File.txt", wd / amxFile)

      val zipFileName = "unzipAllExceptExcludingCertainFiles.zip"
      val zipFile: os.Path = os.zip(
        destination = wd / zipFileName,
        sourcePaths = List(
          wd / "File.txt",
          wd / amxFile,
          wd / "folder1"
        )
      )

      // Unzip file to a destination folder
      val unzippedFolder = os.unzip(
        source = wd / zipFileName,
        destination = wd / "unzipAllExceptExcludingCertainFiles",
        excludePatterns = List(amxFile)
      )

      val paths = os.walk(unzippedFolder)
      assert(paths.length == 3)
      assert(paths.contains(unzippedFolder / "File.txt"))
      assert(paths.contains(unzippedFolder / "folder1" / "one.txt"))
    }

    test("zipStreamFunction") - prep { wd =>
      val zipFileName = "zipStreamFunction.zip"

      val stream = os.write.outputStream(wd / "zipStreamFunction.zip")

      val writable = zip.stream(source = wd / "File.txt")

      writable.writeBytesTo(stream)
      stream.close()

      val unzippedFolder = os.unzip(
        source = wd / zipFileName,
        destination = wd / "zipStreamFunction"
      )

      val paths = os.walk(unzippedFolder)
      assert(paths.length == 1)
      assert(paths.contains(unzippedFolder / "File.txt"))
    }

    test("unzipStreamFunction") - prep { wd =>
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
}
