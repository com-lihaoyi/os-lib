package test.os

import utest.framework.TestPath
import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

object TestUtil {

  val NewLineRegex = "\r\n|\r|\n"

  def isInstalled(executable: String): Boolean = {
    val getPathCmd = if (scala.util.Properties.isWin) "where" else "which"
    os.proc(getPathCmd, executable).call(check = false).exitCode == 0
  }

  def isPython3(): Boolean = {
    os.proc("python", "--version").call(check = false).out.text().startsWith("Python 3.")
  }

  // run Unix command normally, Windows in CMD context
  def proc(command: os.Shellable*) = {
    if (scala.util.Properties.isWin) {
      val cmd = ("CMD.EXE": os.Shellable) :: ("/C": os.Shellable) :: command.toList
      os.proc(cmd: _*)
    } else os.proc(command)
  }

  // 1. when using Git "core.autocrlf true"
  //    some tests would fail when comparing with only \n
  // 2. when using Git "core.autocrlf false"
  //    some tests would fail when comparing with process outputs which produce CRLF strings
  /** Compares two strings, ignoring line-ending style */
  def eqIgnoreNewlineStyle(str1: String, str2: String) = {
    val str1Normalized = str1.replaceAll(NewLineRegex, "\n").replaceAll("\n+", "\n")
    val str2Normalized = str2.replaceAll(NewLineRegex, "\n").replaceAll("\n+", "\n")
    str1Normalized == str2Normalized
  }

  def prep[T](f: os.Path => T)(implicit tp: TestPath, fn: sourcecode.FullName) = {
    val segments = Seq("out", "scratch") ++ fn.value.split('.').drop(2) ++ tp.value

    val directory = Paths.get(segments.mkString("/"))
    if (!Files.exists(directory)) Files.createDirectories(directory.getParent)
    else Files.walkFileTree(
      directory,
      new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes) = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException) = {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        }
      }
    )

    val original = Paths.get(sys.env("OS_TEST_RESOURCE_FOLDER"), "test")
    Files.walkFileTree(
      original,
      new SimpleFileVisitor[Path]() {
        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
          Files.copy(dir, directory.resolve(original.relativize(dir)), LinkOption.NOFOLLOW_LINKS)
          FileVisitResult.CONTINUE
        }

        override def visitFile(file: Path, attrs: BasicFileAttributes) = {
          Files.copy(file, directory.resolve(original.relativize(file)), LinkOption.NOFOLLOW_LINKS)
          FileVisitResult.CONTINUE
        }
      }
    )

    f(os.Path(directory.toAbsolutePath))
  }

  lazy val isDotty = {
    val cl: ClassLoader = Thread.currentThread().getContextClassLoader
    try {
      cl.loadClass("scala.runtime.Scala3RunTime")
      true
    } catch {
      case _: ClassNotFoundException =>
        false
    }
  }
}
