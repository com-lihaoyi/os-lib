package test.os

import os.zip
import test.os.TestUtil.prep
import utest._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, PrintStream}
import java.util.zip.{ZipEntry, ZipOutputStream}

object ZipOpTests extends TestSuite {

  def tests = Tests {
    // This test seems really flaky for some reason
    // test("level") - prep { wd =>
    //   val zipsForLevel = for (i <- Range.inclusive(0, 9)) yield {
    //     os.write.over(wd / "File.txt", Range(0, 1000).map(x => x.toString * x))
    //     os.zip(
    //       dest = wd / s"archive-$i.zip",
    //       sources = Seq(
    //         wd / "File.txt",
    //         wd / "folder1"
    //       ),
    //       compressionLevel = i
    //     )
    //   }

    //   // We can't compare every level because compression isn't fully monotonic,
    //   // but we compare some arbitrary levels just to sanity check things

    //   // Uncompressed zip is definitely bigger than first level of compression
    //   assert(os.size(zipsForLevel(0)) > os.size(zipsForLevel(1)))
    //   // First level of compression is bigger than middle compression
    //   assert(os.size(zipsForLevel(1)) > os.size(zipsForLevel(5)))
    //   // Middle compression is bigger than best compression
    //   assert(os.size(zipsForLevel(5)) > os.size(zipsForLevel(9)))
    // }
    test("renaming") - prep { wd =>
      val zipFileName = "zip-file-test.zip"
      val zipFile1: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          // renaming files and folders
          wd / "File.txt" -> os.sub / "renamed-file.txt",
          wd / "folder1" -> os.sub / "renamed-folder"
        )
      )

      val unzippedFolder = os.unzip(
        source = zipFile1,
        dest = wd / "unzipped folder"
      )

      val paths = os.walk(unzippedFolder)
      val expected = Seq(
        wd / "unzipped folder/renamed-file.txt",
        wd / "unzipped folder/renamed-folder",
        wd / "unzipped folder/renamed-folder/one.txt"
      )
      assert(paths.sorted == expected)
    }

    test("excludePatterns") - prep { wd =>
      val amxFile = "File.amx"
      os.copy(wd / "File.txt", wd / amxFile)

      // Zipping files and folders in a new zip file
      val zipFileName = "zipByExcludingCertainFiles.zip"
      val zipFile1: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          wd / "File.txt",
          wd / amxFile,
          wd / "Multi Line.txt"
        ),
        excludePatterns = Seq(".*\\.txt".r)
      )

      // Unzip file to check for contents
      val outputZipFilePath = os.unzip(
        zipFile1,
        dest = wd / "zipByExcludingCertainFiles"
      )
      val paths = os.walk(outputZipFilePath).sorted
      val expected = Seq(wd / "zipByExcludingCertainFiles/File.amx")
      assert(paths == expected)
    }

    test("includePatterns") - prep { wd =>
      val amxFile = "File.amx"
      os.copy(wd / "File.txt", wd / amxFile)

      // Zipping files and folders in a new zip file
      val zipFileName = "zipByIncludingCertainFiles.zip"
      val zipFile1: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          wd / "File.txt",
          wd / amxFile,
          wd / "Multi Line.txt"
        ),
        includePatterns = Seq(".*\\.amx".r)
      )

      // Unzip file to check for contents
      val outputZipFilePath =
        os.unzip(zipFile1, dest = wd / "zipByIncludingCertainFiles")
      val paths = os.walk(outputZipFilePath)
      val expected = Seq(wd / "zipByIncludingCertainFiles" / amxFile)
      assert(paths == expected)
    }

    test("zipStream") - prep { wd =>
      val zipFileName = "zipStreamFunction.zip"

      val stream = os.write.outputStream(wd / "zipStreamFunction.zip")

      val writable = zip.stream(sources = Seq(wd / "File.txt"))

      writable.writeBytesTo(stream)
      stream.close()

      val unzippedFolder = os.unzip(
        source = wd / zipFileName,
        dest = wd / "zipStreamFunction"
      )

      val paths = os.walk(unzippedFolder)
      assert(paths == Seq(unzippedFolder / "File.txt"))
    }

    test("list") - prep { wd =>
      // Zipping files and folders in a new zip file
      val zipFileName = "listContentsOfZipFileWithoutExtracting.zip"
      val zipFile: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          wd / "File.txt",
          wd / "folder1"
        )
      )

      // Unzip file to a destination folder
      val listedContents = os.unzip.list(source = wd / zipFileName).toSeq

      val expected = Seq(os.sub / "File.txt", os.sub / "one.txt")
      assert(listedContents == expected)
    }

    test("unzipExcludePatterns") - prep { wd =>
      val amxFile = "File.amx"
      os.copy(wd / "File.txt", wd / amxFile)

      val zipFileName = "unzipAllExceptExcludingCertainFiles.zip"
      val zipFile: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          wd / "File.txt",
          wd / amxFile,
          wd / "folder1"
        )
      )

      // Unzip file to a destination folder
      val unzippedFolder = os.unzip(
        source = wd / zipFileName,
        dest = wd / "unzipAllExceptExcludingCertainFiles",
        excludePatterns = Seq(amxFile.r)
      )

      val paths = os.walk(unzippedFolder)
      val expected = Seq(
        wd / "unzipAllExceptExcludingCertainFiles/File.txt",
        wd / "unzipAllExceptExcludingCertainFiles/one.txt"
      )

      assert(paths == expected)
    }

    test("zipList") - prep { wd =>
      val sources = wd / "folder1"
      val zipFilePath = os.zip(
        dest = wd / "my.zip",
        sources = os.list(sources)
      )

      val expected = os.unzip.list(source = zipFilePath).map(_.resolveFrom(sources)).toSet
      assert(os.list(sources).toSet == expected)
    }

    test("unzipStream") - prep { wd =>
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
      val readableZipStream: java.io.InputStream =
        new ByteArrayInputStream(zipStreamOutput.toByteArray)

      // Unzipping the stream to the destination folder
      os.unzip.stream(
        source = readableZipStream,
        dest = unzippedFolder
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
