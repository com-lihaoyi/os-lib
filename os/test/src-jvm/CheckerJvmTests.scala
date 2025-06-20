package test.os

import test.os.TestUtil._
import utest._

object CheckerJvmTests extends TestSuite {

  def tests: Tests = Tests {
    // restricted directory
    val rd = os.Path(sys.env("OS_TEST_RESOURCE_FOLDER")) / "restricted"

    test("zip") - prepChecker { wd =>
      intercept[WriteDenied] {
        os.zip(
          dest = rd / "zipped.zip",
          sources = Seq(
            wd / "File.txt",
            wd / "folder1"
          )
        )
      }
      os.exists(rd / "zipped.zip") ==> false

      intercept[ReadDenied] {
        os.zip(
          dest = wd / "zipped.zip",
          sources = Seq(
            wd / "File.txt",
            rd / "folder1"
          )
        )
      }
      os.exists(wd / "zipped.zip") ==> false

      val zipFile = os.zip(
        wd / "zipped.zip",
        Seq(
          wd / "File.txt",
          wd / "folder1"
        )
      )

      val unzipDir = os.unzip(zipFile, wd / "unzipped")
      os.walk(unzipDir).sorted ==> Seq(
        unzipDir / "File.txt",
        unzipDir / "one.txt"
      )
    }
    test("unzip") - prepChecker { wd =>
      val zipFileName = "zipped.zip"
      val zipFile: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          wd / "File.txt",
          wd / "folder1"
        )
      )

      intercept[WriteDenied] {
        os.unzip(
          source = zipFile,
          dest = rd / "unzipped"
        )
      }
      os.exists(rd / "unzipped") ==> false

      val unzipDir = os.unzip(
        source = zipFile,
        dest = wd / "unzipped"
      )
      os.walk(unzipDir).length ==> 2
    }
  }
}
