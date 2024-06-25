package test.os

import java.io._
import java.nio.charset.StandardCharsets

import os._
import utest._
import TestUtil.prep
import scala.util.Try

object ProcessPipelineTests extends TestSuite {
  val scriptFolder = pwd / "os" / "test" / "resources" / "scripts"

  lazy val scalaHome = sys.env("SCALA_HOME")

  def isWindows = System.getProperty("os.name").toLowerCase().contains("windows")

  def scriptProc(name: String, args: String*): Seq[String] =
    Seq(
      "java",
      "-jar",
      name
    ) ++ args.toSeq

  def writerProc(n: Int, wait: Int, debugOutput: Boolean = true): Seq[String] =
    scriptProc(sys.env("TEST_JAR_WRITER_ASSEMBLY"), n.toString, wait.toString, debugOutput.toString)
  def readerProc(n: Int, wait: Int, debugOutput: Boolean = true): Seq[String] =
    scriptProc(sys.env("TEST_JAR_READER_ASSEMBLY"), n.toString, wait.toString, debugOutput.toString)
  def exitProc(code: Int, wait: Int): Seq[String] =
    scriptProc(sys.env("TEST_JAR_EXIT_ASSEMBLY"), code.toString, wait.toString)

  val commonTests = Tests {
    test("pipelineCall") {
      val resultLines = os.proc(writerProc(10, 10))
        .pipeTo(os.proc(readerProc(10, 10)))
        .call().out.lines().toSeq

      val expectedLog = (0 until 10).map(i => s"Read: Hello $i")
      assert(expectedLog.forall(resultLines.contains))
    }

    test("pipelineSpawn") {
      val buffer = new collection.mutable.ArrayBuffer[String]()
      val p = os.proc(writerProc(10, 10))
        .pipeTo(os.proc(readerProc(10, 10)))
        .spawn(stdout = os.ProcessOutput.Readlines(s => buffer.append(s)))

      p.waitFor()

      val expectedLog = (0 until 10).map(i => s"Read: Hello $i")
      assert(expectedLog.forall(buffer.contains))
    }

    test("longPipepelineSpawn") {
      val buffer = new collection.mutable.ArrayBuffer[String]()
      val p = os.proc(writerProc(10, 10))
        .pipeTo(os.proc(readerProc(10, 10)))
        .pipeTo(os.proc(readerProc(10, 10)))
        .pipeTo(os.proc(readerProc(10, 10)))
        .spawn(stdout = os.ProcessOutput.Readlines(s => buffer.append(s)))

      p.waitFor()

      val expectedLog =
        (0 until 10).map(i => s"Read: Read: Read: Hello $i") // each reader appends "Read:"
      assert(expectedLog.forall(buffer.contains))
    }

    test("pipelineSpawnWithStdin") {
      test - prep { wd =>
        val buffer = new collection.mutable.ArrayBuffer[String]()
        val p = os.proc(readerProc(1, 10))
          .pipeTo(os.proc(readerProc(1, 10)))
          .spawn(
            stdin = wd / "File.txt",
            stdout = os.ProcessOutput.Readlines(s => buffer.append(s))
          )

        p.waitFor()

        assert(buffer.contains("Read: Read: I am cow"))
      }
    }

    test("pipelineSpawnWithStderr") {
      val buffer = new collection.mutable.ArrayBuffer[String]()
      val p = os.proc(writerProc(10, 10))
        .pipeTo(os.proc(readerProc(10, 10)))
        .spawn(stderr = os.ProcessOutput.Readlines(s => synchronized { buffer.append(s) }))

      p.waitFor()

      val expectedLog = (0 until 10).flatMap(i => Seq(s"At: $i", s"Written $i"))

      assert(expectedLog.forall(buffer.contains))
    }

    test("pipelineWithoutPipefail") {
      val p = os.proc(exitProc(0, 300))
        .pipeTo(os.proc(exitProc(213, 100)))
        .pipeTo(os.proc(exitProc(0, 400)))
        .spawn(pipefail = false)

      p.waitFor()
      assert(p.exitCode() == 0)
    }

    test("pipelineWithPipefail") {
      val p = os.proc(exitProc(0, 300))
        .pipeTo(os.proc(exitProc(1, 100)))
        .pipeTo(os.proc(exitProc(0, 400)))
        .spawn(pipefail = true)

      p.waitFor()
      assert(p.exitCode() == 1)
    }
  }

  val nonWindowsTests = Tests {
    test("brokenPipe") {
      val p = os.proc(writerProc(-1, 0, false))
        .pipeTo(os.proc(readerProc(3, 0, false)))
        .spawn()

      p.waitFor(10000)
      val finished = !p.isAlive()
      p.destroy()

      assert(finished)
    }

    test("brokenPipeNotHandled") {
      val p = os.proc(writerProc(-1, 0, false))
        .pipeTo(os.proc(readerProc(3, 0, false)))
        .spawn(handleBrokenPipe = false)

      p.waitFor(1000)
      val alive = p.isAlive()
      p.destroy()

      assert(alive)
    }

    test("longBrokenPipePropagate") {
      val p = os.proc(writerProc(-1, 0, false))
        .pipeTo(os.proc(readerProc(-1, 0, false)))
        .pipeTo(os.proc(readerProc(-1, 0, false)))
        .pipeTo(os.proc(readerProc(3, 0, false)))
        .spawn()

      p.waitFor(30000) // long to avoid flaky tests
      val finished = !p.isAlive()
      p.destroy()

      assert(finished)
    }
  }

  override def tests: Tests = if (!isWindows) commonTests ++ nonWindowsTests else commonTests
}
