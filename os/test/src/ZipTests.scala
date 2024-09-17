package test.os

import test.os.TestUtil.prep
import utest._

import scala.collection.immutable.Seq

object ZipTests extends TestSuite {

  // on unix it is 81 bytes, win adds 3 bytes (3 \r characters)
  private val multilineSizes = Set[Long](81, 84)

  def tests = Tests {

    test("zipOps") {

      test - prep { wd =>
        val zipFile = os.zip(wd / "zip-test.zip")
        os.copy(wd / "File.txt", zipFile / "File.txt")
        os.copy(wd / "folder1", zipFile / "folder1")
        os.copy(wd / "folder2", zipFile / "folder2")
        zipFile.close()

        val zipFile2 = os.zip(wd / "zip-test.zip")
        os.list(zipFile2.root()).map(_.toString) ==>
          Vector(os.root / "File.txt", os.root / "folder1", os.root / "folder2").map(_.toString)
        os.remove.all(zipFile2 / "folder2")
        os.remove(zipFile2 / "File.txt")
        zipFile2.close()

        val zipFile3 = os.zip(wd / "zip-test.zip")
        os.list(zipFile3.root()).map(_.toString) ==> Vector(os.root / "folder1").map(_.toString)
        zipFile3.close()

      }
    }
  }

}
