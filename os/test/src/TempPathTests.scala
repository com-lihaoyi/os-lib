package test.os

import os._
// if running with scala 2.13+, this can be the regular `scala.util.Using`
import os.util.Using
import utest.{assert => _, _}

object TempPathTests extends TestSuite{
  val tests = Tests {

    test("convenience methods") {
      test("temp.withFile") {
        var tempFilePath: String = null
        os.temp.withFile { file =>
          tempFilePath = file.toString
          assert(os.exists(file))
        }
        assert(!os.exists(os.Path(tempFilePath)), s"temp file did not get auto-deleted after `Using` block: $tempFilePath")
      }
      test("temp.withDir") {
        var tempDirPath: String = null
        var tempFilePath: String = null
        os.temp.withDir { dir =>
          val file = dir / "somefile"
          tempDirPath = dir.toString
          tempFilePath = file.toString
          os.write(file, "some content")
          assert(os.exists(dir))
          assert(os.exists(file))
        }
        assert(!os.exists(os.Path(tempDirPath)), s"temp dir did not get auto-deleted after `Using` block: $tempDirPath")
      }
    }

    test("delete after `Using` block") {
      test("single file") {
        var tempFilePath: String = null
        Using(os.temp()) { file =>
          tempFilePath = file.toString
          assert(os.exists(file))
        }
        assert(!os.exists(os.Path(tempFilePath)), s"temp file did not get auto-deleted after `Using` block: $tempFilePath")
      }
      test("directory") {
        var tempDirPath: String = null
        var tempFilePath: String = null
        Using(os.temp.dir()) { dir =>
          val file = dir / "somefile"
          tempDirPath = dir.toString
          tempFilePath = file.toString
          os.write(file, "some content")
          assert(os.exists(dir))
          assert(os.exists(file))
        }
        assert(!os.exists(os.Path(tempDirPath)), s"temp dir did not get auto-deleted after `Using` block: $tempDirPath")
      }

      test("multiple files") {
        var tempFilePaths: Seq[String] = Nil
        Using.Manager { use =>
          val file1 = use(os.temp())
          val file2 = use(os.temp())
          val files = Seq(file1, file2)
          tempFilePaths = files.map(_.toString)
          files.foreach(file => assert(os.exists(file)))
        }
        tempFilePaths.foreach { file =>
          assert(!os.exists(os.Path(file)), s"temp file did not get auto-deleted after `Using` block: $file")
        }
      }
    }
  }
}
