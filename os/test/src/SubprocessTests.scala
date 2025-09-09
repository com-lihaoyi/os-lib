package test.os

import java.io._
import java.nio.charset.StandardCharsets

import os._
import utest._

import scala.collection.mutable

object SubprocessTests extends TestSuite {
  val scriptFolder = os.Path(sys.env("OS_TEST_RESOURCE_FOLDER")) / "test"

  val lsCmd = if (scala.util.Properties.isWin) "dir" else "ls"

  val tests = Tests {
    test("lines") {
      val res = TestUtil.proc(lsCmd, scriptFolder).call()
      assert(
        res.out.lines().exists(_.contains("File.txt")),
        res.out.lines().exists(_.contains("folder1")),
        res.out.lines().exists(_.contains("folder2"))
      )
    }
    test("string") {
      val res = TestUtil.proc(lsCmd, scriptFolder).call()
      assert(
        res.out.text().contains("File.txt"),
        res.out.text().contains("folder1"),
        res.out.text().contains("folder2")
      )
    }
    test("bytes") {
      if (Unix()) {
        val res = proc(scriptFolder / "misc/echo", "abc").call()
        val listed = res.out.bytes
        listed ==> "abc\n".getBytes
      }
    }
    test("chained") {
      proc("git", "init").call()
      os.write.over(os.pwd / "Readme.adoc", "hello")
      assert(
        proc("git", "init").call().out.text().contains("Reinitialized existing Git repository"),
        proc("git", "init").call().out.text().contains("Reinitialized existing Git repository"),
        TestUtil.proc(lsCmd, pwd).call().out.text().contains("Readme.adoc")
      )
    }
    test("basicList") {
      val files = List("Readme.adoc", "build.sc")
      os.write.over(os.pwd / "Readme.adoc", "hello")
      os.write.over(os.pwd / "build.sc", "world")
      val output = TestUtil.proc(lsCmd, files).call().out.text()
      assert(files.forall(output.contains))
    }
    test("listMixAndMatch") {
      val stuff = List("I", "am", "bovine")
      val result = TestUtil.proc("echo", "Hello,", stuff, "hear me roar").call()
      if (Unix())
        assert(result.out.text().contains("Hello, " + stuff.mkString(" ") + " hear me roar"))
      else // win quotes multiword args
        assert(result.out.text().contains("Hello, " + stuff.mkString(" ") + " \"hear me roar\""))
    }
    test("failures") {
      val ex = intercept[os.SubprocessException] {
        TestUtil.proc(lsCmd, "does-not-exist").call(check = true, stderr = os.Pipe)
      }
      val res: CommandResult = ex.result
      assert(
        res.exitCode != 0,
        res.err.text().contains("No such file or directory") || // unix
          res.err.text().contains("File Not Found") // win
      )
    }

    test("filebased") {
      if (Unix()) {
        assert(proc(scriptFolder / "misc/echo", "HELLO").call().out.lines().mkString == "HELLO")

        val res: CommandResult =
          proc(root / "bin/bash", "-c", "echo 'Hello'$ENV_ARG").call(
            env = Map("ENV_ARG" -> "123")
          )

        // Enhanced debugging: show exit code and raw output on failure
        if (res.exitCode != 0) {
          throw new Exception(
            s"Subprocess failed with exit code ${res.exitCode}, stderr: '${res.err.text()}'"
          )
        }
        val actualOutput = res.out.text().trim()
        val expectedOutput = "Hello123"
        if (actualOutput != expectedOutput) {
          throw new Exception(
            s"Output mismatch: expected '$expectedOutput', got '$actualOutput' (${actualOutput.length} chars, exit code: ${res.exitCode})"
          )
        }
        assert(actualOutput == expectedOutput)
      }
    }
    test("filebased2") {
      if (Unix()) {
        val possiblePaths = Seq(root / "bin", root / "usr/bin").map { pfx => pfx / "echo" }
        val res = proc("which", "echo").call()
        val echoRoot = Path(res.out.text().trim())
        assert(possiblePaths.contains(echoRoot))

        assert(proc(echoRoot, "HELLO").call().out.lines() == Seq("HELLO"))
      }
    }

    test("charSequence") {
      val charSequence = new StringBuilder("This is a CharSequence")
      val cmd = Seq(
        "echo",
        charSequence
      )
      val res = proc(cmd).call()
      assert(res.out.text().trim() == charSequence.toString())
    }

    test("envArgs.doubleQuotesExpand-1") {
      if (Unix()) {
        val res0 = proc("bash", "-c", "echo \"Hello$ENV_ARG\"").call(env = Map("ENV_ARG" -> "12"))
        val expectedLines = Seq("Hello12")
        val actualLines = res0.out.lines()
        if (actualLines != expectedLines) {
          throw new Exception(
            s"envArgs.doubleQuotesExpand-1 failed: expected $expectedLines, got $actualLines (exit code: ${res0.exitCode})"
          )
        }
        assert(actualLines == expectedLines)
      }
    }
    test("envArgs.doubleQuotesExpand-2") {
      if (Unix()) {
        val res1 = proc("bash", "-c", "echo \"Hello$ENV_ARG\"").call(env = Map("ENV_ARG" -> "12"))
        val expectedLines = Seq("Hello12")
        val actualLines = res1.out.lines()
        if (actualLines != expectedLines) {
          throw new Exception(
            s"envArgs.doubleQuotesExpand-2 failed: expected $expectedLines, got $actualLines (exit code: ${res1.exitCode})"
          )
        }
        assert(actualLines == expectedLines)
      }
    }
    test("envArgs.singleQuotesNoExpand") {
      if (Unix()) {
        val res2 = proc("bash", "-c", "echo 'Hello$ENV_ARG'").call(env = Map("ENV_ARG" -> "12"))
        assert(res2.out.lines() == Seq("Hello$ENV_ARG"))
      }
    }
    test("envArgs.concatSingleQuotedAndVar") {
      if (Unix()) {
        val res3 = proc("bash", "-c", "echo 'Hello'$ENV_ARG").call(env = Map("ENV_ARG" -> "123"))
        assert(res3.out.lines() == Seq("Hello123"))
      }
    }
    test("envArgs.propagateEnv=false") {
      if (Unix()) {
        // TEST_SUBPROCESS_ENV env should be set in forkEnv in build.sc
        assert(sys.env.get("TEST_SUBPROCESS_ENV") == Some("value"))
        val res4 = proc("bash", "-c", "echo \"$TEST_SUBPROCESS_ENV\"").call(
          env = Map.empty,
          propagateEnv = false
        ).out.lines()
        assert(res4 == Seq(""))
      }
    }
    test("envArgs.propagateEnv=true") {
      if (Unix()) {
        // TEST_SUBPROCESS_ENV env should be set in forkEnv in build.sc
        assert(sys.env.get("TEST_SUBPROCESS_ENV") == Some("value"))
        val res5 = proc("bash", "-c", "echo \"$TEST_SUBPROCESS_ENV\"").call(
          env = Map.empty,
          propagateEnv = true
        ).out.lines()
        assert(res5 == Seq("value"))
      }
    }
    test("envWithValue") {
      if (Unix()) {
        val variableName = "TEST_ENV_FOO"
        val variableValue = "bar"
        def envValue() = os.proc(
          "bash",
          "-c",
          s"""if [ -z $${$variableName+x} ]; then echo "unset"; else echo "$$$variableName"; fi"""
        ).call().out.lines().head

        val before = envValue()
        assert(before == "unset")

        os.SubProcess.env.withValue(Map(variableName -> variableValue)) {
          val res = envValue()
          assert(res == variableValue)
        }

        val after = envValue()
        assert(after == "unset")
      }
    }
    test("multiChunk") {
      // Make sure that in the case where multiple chunks are being read from
      // the subprocess in quick succession, we ensure that the output handler
      // callbacks are properly ordered such that the output is aggregated
      // correctly
      test("bashC") {
        if (TestUtil.isInstalled("python")) {
          os.proc(
            "python",
            "-c",
            """import sys, time
              |for i in range(5):
              |  for j in range(10):
              |    sys.stdout.write(str(j))
              |    # Make sure it comes as multiple chunks, but close together!
              |    # Vary how close they are together to try and trigger race conditions
              |    time.sleep(0.00001 * i)
              |    sys.stdout.flush()
        """.stripMargin
          ).call().out.text() ==>
            "01234567890123456789012345678901234567890123456789"
        }
      }
      test("jarTf") {
        // This was the original repro for the multi-chunk concurrency bugs
        val jarFile = os.Path(sys.env("OS_TEST_RESOURCE_FOLDER")) / "misc/out.jar"
        assert(TestUtil.eqIgnoreNewlineStyle(
          os.proc("jar", "-tf", jarFile).call().out.text(),
          """META-INF/MANIFEST.MF
            |test/FooTwo.class
            |test/Bar.class
            |test/BarTwo.class
            |test/Foo.class
            |test/BarThree.class
            |hello.txt
            |""".stripMargin
        ))
      }
    }
    test("workingDirectory") {
      val listed1 = TestUtil.proc(lsCmd).call(cwd = pwd)
      val listed2 = TestUtil.proc(lsCmd).call(cwd = pwd / up)

      assert(listed2 != listed1)
    }
    test("customWorkingDir") {
      val res1 = TestUtil.proc(lsCmd).call(cwd = pwd) // explicitly
      // or implicitly
      val res2 = TestUtil.proc(lsCmd).call()
    }

    test("fileCustomWorkingDir") {
      if (Unix()) {
        val output = proc(scriptFolder / "misc/echo_with_wd", "HELLO").call(cwd = root / "usr")
        assert(output.out.lines() == Seq("HELLO /usr"))
      }
    }
    test("dynamicPwd") {
      // Windows doesnt have bash installed so a bit inconvenient
      // to run these subprocesses for testing
      if (!scala.util.Properties.isWin) {
        val outsidePwd = os.pwd
        val tmp0 = os.temp.dir()
        val tmp = os.followLink(tmp0).getOrElse(tmp0)
        val x = proc("bash", "-c", "pwd").call()
        val y = os.dynamicPwd.withValue(tmp) {
          proc("bash", "-c", "pwd").call()
        }

        val z = proc("bash", "-c", "pwd").call()
        assert(outsidePwd.toString != tmp.toString)
        assert(x.out.trim() == outsidePwd.toString)
        assert(y.out.trim() == tmp.toString)
        assert(z.out.trim() == outsidePwd.toString)
      }
    }

    // Stress test to help reproduce intermittent subprocess failures seen in CI
    test("stressSubprocess") {
      if (Unix()) {
        val iterations = sys.env.get("SUBPROCESS_STRESS_ITERATIONS").map(_.toInt).getOrElse(10)
        var failures = 0
        var successes = 0

        for (i <- 1 to iterations) {
          try {
            // Test the exact same pattern that's failing in CI
            val res = proc("bash", "-c", "echo 'Hello'$ENV_ARG").call(env = Map("ENV_ARG" -> "123"))
            val expected = "Hello123"
            val actual = res.out.text().trim()

            if (res.exitCode != 0) {
              println(s"Iteration $i: subprocess failed with exit code ${res.exitCode}")
              failures += 1
            } else if (actual != expected) {
              println(s"Iteration $i: output mismatch - expected '$expected', got '$actual'")
              failures += 1
            } else {
              successes += 1
            }

            // Add a small delay to potentially trigger race conditions
            Thread.sleep(1)

          } catch {
            case ex: Throwable =>
              println(s"Iteration $i: exception - ${ex.getMessage}")
              failures += 1
          }
        }

        println(
          s"Stress test completed: $successes successes, $failures failures out of $iterations iterations"
        )

        // Allow up to 10% failure rate for now to gather data
        val failureRate = failures.toDouble / iterations
        if (failureRate > 0.1) {
          throw new Exception(
            s"High failure rate in stress test: ${(failureRate * 100).toInt}% ($failures/$iterations)"
          )
        }
      }
    }
  }
}
