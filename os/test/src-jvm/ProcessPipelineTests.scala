package test.os

import java.io._
import java.nio.charset.StandardCharsets

import os._
import utest._
import TestUtil.prep
import scala.util.Try

object ProcessPipelineTests extends TestSuite {
  val scriptFolder = pwd / "os" / "test" / "resources" / "scripts"

  def isWindows = System.getProperty("os.name").toLowerCase().contains("windows")
  def isUnix = Try(os.proc("uname").call().exitCode).toOption.exists(_ == 0)

  def scriptProc(name: String, args: String*): Seq[String] = Seq("scala", (scriptFolder / name).toString()) ++ args.toSeq

  def writerProc(n: Int, wait: Int): Seq[String] = scriptProc("writer.scala", n.toString, wait.toString)
  def readerProc(n: Int, wait: Int): Seq[String] = scriptProc("reader.scala", n.toString, wait.toString)
  def exitProc(code: Int, wait: Int): Seq[String] = scriptProc("exit.scala", code.toString, wait.toString)

  val tests = Tests {
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

      val expectedLog = (0 until 10).map(i => s"Read: Read: Read: Hello $i") // each reader appends "Read:"
      assert(expectedLog.forall(buffer.contains))
    }

    test("pipelineSpawnWithStdin") {
      test - prep { wd =>
        val buffer = new collection.mutable.ArrayBuffer[String]()
        val p = os.proc(readerProc(1, 10))
          .pipeTo(os.proc(readerProc(1, 10)))
          .spawn(stdin = wd / "File.txt", stdout = os.ProcessOutput.Readlines(s => buffer.append(s)))
        
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
      println (p.exitCode())
      assert(p.exitCode == 0)
    }

    test("pipelineWithPipefail") {
      val p = os.proc(exitProc(0, 300))
        .pipeTo(os.proc(exitProc(213, 100)))
        .pipeTo(os.proc(exitProc(0, 400)))
        .spawn(pipefail = true)
      
      p.waitFor()
      println (p.exitCode())
      assert(p.exitCode == 213)
    }

    test("imitatePipeline") {
      test("pipelineCall") {
        val resultLines = os.proc(writerProc(10, 10))
          .pipeTo(os.proc(readerProc(10, 10)))
          .call(imitatePipeline = true).out.lines().toSeq

        val expectedLog = (0 until 10).map(i => s"Read: Hello $i")
        assert(expectedLog.forall(resultLines.contains))
      }

      test("pipelineSpawn") {
        val buffer = new collection.mutable.ArrayBuffer[String]()
        val p = os.proc(writerProc(10, 10))
          .pipeTo(os.proc(readerProc(10, 10)))
          .spawn(stdout = os.ProcessOutput.Readlines(s => buffer.append(s)), imitatePipeline = true)

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
          .spawn(stdout = os.ProcessOutput.Readlines(s => buffer.append(s)), imitatePipeline = true)

        p.waitFor()

        val expectedLog = (0 until 10).map(i => s"Read: Read: Read: Hello $i") // each reader appends "Read:"
        assert(expectedLog.forall(buffer.contains))
      }

      test("brokenPipe") {
        val stderr = new collection.mutable.ArrayBuffer[String]()
        val p = os.proc(writerProc(10, 10))
          .pipeTo(os.proc(readerProc(5, 10)))
          .spawn(stderr = os.ProcessOutput.Readlines(s => stderr.append(s)), imitatePipeline = true)

        p.waitFor()

        assert(true) // what?
      }
    }
    
    test("jvm9Pipeline") {
      test("brokenPipe") {
        val stderr = new collection.mutable.ArrayBuffer[String]()
        val p = os.proc(writerProc(10, 10))
          .pipeTo(os.proc(readerProc(5, 10)))
          .spawn(stderr = os.ProcessOutput.Readlines(s => stderr.append(s)), imitatePipeline = false)

        p.waitFor()

        assert(stderr.contains("Got PIPE - exiting"))
      }
    }
  }
}