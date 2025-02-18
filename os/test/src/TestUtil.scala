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

  def prepChecker[T](f: os.Path => T)(implicit tp: TestPath, fn: sourcecode.FullName): T =
    prep(wd => os.checker.withValue(AccessChecker(wd))(f(wd)))

  object AccessChecker {
    def apply(roots: os.Path*): AccessChecker = AccessChecker(roots, roots)
  }

  case class AccessChecker(readRoots: Seq[os.Path], writeRoots: Seq[os.Path]) extends os.Checker {

    def onRead(path: os.ReadablePath): Unit = {
      path match {
        case path: os.Path =>
          if (!readRoots.exists(path.startsWith)) throw ReadDenied(path, readRoots)
        case _ =>
      }
    }

    def onWrite(path: os.Path): Unit = {
      // skip check when not writing to filesystem (like when writing to a zip file)
      if (path.wrapped.getFileSystem.provider().getScheme == "file") {
        if (!writeRoots.exists(path.startsWith)) throw WriteDenied(path, writeRoots)
      }
    }
  }

  object Unchecked {

    def apply[T](thunk: => T): T =
      os.checker.withValue(os.Checker.Nop)(thunk)

    def scope[T](acquire: => Unit, release: => Unit)(thunk: => T): T = {
      apply(acquire)
      try thunk
      finally apply(release)
    }
  }

  case class ReadDenied(requested: os.Path, allowed: Seq[os.Path])
      extends Exception(
        s"Cannot read from $requested. Read is ${
            if (allowed.isEmpty) "not permitted"
            else s"restricted to ${allowed.mkString(", ")}"
          }."
      )

  case class WriteDenied(requested: os.Path, allowed: Seq[os.Path])
      extends Exception(
        s"Cannot write to $requested. Write is ${
            if (allowed.isEmpty) "not permitted"
            else s"restricted to ${allowed.mkString(", ")}"
          }."
      )

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
