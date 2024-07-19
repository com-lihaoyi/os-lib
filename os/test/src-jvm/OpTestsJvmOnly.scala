package test.os

import java.nio.file.NoSuchFileException
import java.nio.{file => nio}

import utest._
import os.{GlobSyntax, /}
import java.nio.charset.Charset

object OpTestsJvmOnly extends TestSuite {

  val tests = Tests {
    val res = os.pwd / "os" / "test" / "resources" / "test"
    val testFolder = os.pwd / "out" / "scratch" / "test"
    test("lsRecPermissions") {
      if (Unix()) {
        assert(os.walk(os.root / "var" / "run").nonEmpty)
      }
    }
    test("readResource") {
      test("positive") {
        test("absolute") {
          val contents = os.read(os.resource / "test" / "os" / "folder" / "file.txt")
          assert(contents.contains("file contents lols"))

          val cl = getClass.getClassLoader
          val contents2 = os.read(os.resource(cl) / "test" / "os" / "folder" / "file.txt")
          assert(contents2.contains("file contents lols"))
        }

        test("relative") {
          val cls = classOf[_root_.test.os.Testing]
          val contents = os.read(os.resource(cls) / "folder" / "file.txt")
          assert(contents.contains("file contents lols"))

          val contents2 = os.read(os.resource(getClass) / "folder" / "file.txt")
          assert(contents2.contains("file contents lols"))
        }
      }
      test("negative") {
        test - intercept[os.ResourceNotFoundException] {
          os.read(os.resource / "folder" / "file.txt")
        }

        test - intercept[os.ResourceNotFoundException] {
          os.read(
            os.resource(classOf[_root_.test.os.Testing]) / "test" / "os" / "folder" / "file.txt"
          )
        }
        test - intercept[os.ResourceNotFoundException] {
          os.read(os.resource(getClass) / "test" / "os" / "folder" / "file.txt")
        }
        test - intercept[os.ResourceNotFoundException] {
          os.read(os.resource(getClass.getClassLoader) / "folder" / "file.txt")
        }
      }
    }
    test("charset") {

      val d = testFolder / "readWrite"
      os.makeDir.all(d)
      os.write.over(d / "charset.txt", "funcionó".getBytes(Charset.forName("Windows-1252")))
      assert(os.read.lines(
        d / "charset.txt",
        Charset.forName("Windows-1252")
      ).head == "funcionó")
    }

    test("listNonExistentFailure") - {
      val d = testFolder / "readWrite"
      intercept[nio.NoSuchFileException](os.list(d / "nonexistent"))
    }

    // Not sure why this doesn't work on native
    test("redirectSubprocessInheritedOutput") {
      if (Unix()) { // relies on bash scripts that don't run on windows
        val scriptFolder = os.pwd / "os" / "test" / "resources" / "test"
        val lines = collection.mutable.Buffer.empty[String]
        os.Inherit.out.withValue(os.ProcessOutput.Readlines(lines.append(_))) {
          os.proc(scriptFolder / "misc" / "echo_with_wd", "HELLO\nWorld").call(
            cwd = os.root / "usr",
            stdout = os.Inherit
          )
        }
        assert(lines == Seq("HELLO", "World /usr"))
      }
    }
  }
}
