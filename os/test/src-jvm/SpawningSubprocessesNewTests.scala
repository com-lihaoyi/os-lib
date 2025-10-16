package test.os

import java.io.{BufferedReader, InputStreamReader}
import os.ProcessOutput

import scala.collection.mutable
import test.os.TestUtil.prep
import utest._

import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util

object SpawningSubprocessesNewTests extends TestSuite {

  def tests = Tests {
    test("call") {
      test - prep { wd =>
        if (Unix()) {
          val res = os.call(cmd = ("ls", wd / "folder2"))

          res.exitCode ==> 0

          res.out.text() ==>
            """nestedA
              |nestedB
              |""".stripMargin

          res.out.trim() ==>
            """nestedA
              |nestedB""".stripMargin

          res.out.lines() ==> Seq(
            "nestedA",
            "nestedB"
          )

          res.out.bytes

          val thrown = intercept[os.SubprocessException] {
            os.call(cmd = ("ls", "doesnt-exist"), cwd = wd)
          }

          assert(thrown.result.exitCode != 0)

          val fail =
            os.call(cmd = ("ls", "doesnt-exist"), cwd = wd, check = false, stderr = os.Pipe)

          assert(fail.exitCode != 0)

          fail.out.text() ==> ""

          assert(fail.err.text().contains("No such file or directory"))

          // You can pass in data to a subprocess' stdin
          val hash = os.call(cmd = ("shasum", "-a", "256"), stdin = "Hello World")
          hash.out.trim() ==> "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e  -"

          // Taking input from a file and directing output to another file
          os.call(cmd = ("base64"), stdin = wd / "File.txt", stdout = wd / "File.txt.b64")

          val expectedB64 = "SSBhbSBjb3c=\n"
          val actualB64 = os.read(wd / "File.txt.b64")
          if (actualB64 != expectedB64) {
            throw new Exception(
              s"base64 output mismatch: expected '$expectedB64', got '$actualB64' (${actualB64.length} chars)"
            )
          }
          assert(actualB64 == expectedB64)

          if (false) {
            os.call(cmd = ("vim"), stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
          }
        }
      }
      test - prep { wd =>
        if (Unix()) {
          val ex = intercept[os.SubprocessException] {
            os.call(cmd = ("bash", "-c", "echo 123; sleep 10; echo 456"), timeout = 2000)
          }

          ex.result.out.trim() ==> "123"
        }
      }
    }
    test("stream") {
      test - prep { wd =>
        if (Unix()) {
          var lineCount = 1
          os.call(
            cmd = ("find", "."),
            cwd = wd,
            stdout =
              os.ProcessOutput((buf, len) => lineCount += buf.slice(0, len).count(_ == '\n'))
          )
          lineCount ==> 22
        }
      }
      test - prep { wd =>
        if (Unix()) {
          var lineCount = 1
          os.call(
            cmd = ("find", "."),
            cwd = wd,
            stdout = os.ProcessOutput.Readlines(line => lineCount += 1)
          )
          lineCount ==> 22
        }
      }
    }

    test("spawn python") {
      test - prep { wd =>
        if (TestUtil.isInstalled("python") && Unix()) {
          // Start a long-lived python process which you can communicate with
          val sub = os.spawn(
            cmd = (
              "python",
              "-u",
              "-c",
              if (TestUtil.isPython3()) "while True: print(eval(input()))"
              else "while True: print(eval(raw_input()))"
            ),
            cwd = wd
          )

          // Sending some text to the subprocess
          sub.stdin.write("1 + 2")
          sub.stdin.writeLine("+ 4")
          sub.stdin.flush()
          sub.stdout.readLine() ==> "7"

          sub.stdin.write("'1' + '2'")
          sub.stdin.writeLine("+ '4'")
          sub.stdin.flush()
          sub.stdout.readLine() ==> "124"

          // Sending some bytes to the subprocess
          sub.stdin.write("1 * 2".getBytes)
          sub.stdin.write("* 4\n".getBytes)
          sub.stdin.flush()
          sub.stdout.read() ==> '8'.toByte

          sub.destroy()
        }
      }
    }
    test("spawn curl") {
      if (
        Unix() &&
        TestUtil.isInstalled("curl") &&
        TestUtil.isInstalled("gzip") &&
        TestUtil.isInstalled("shasum") &&
        TestUtil.canFetchUrl(ExampleResourcess.RemoteReadme.url)
      ) {
        // You can chain multiple subprocess' stdin/stdout together
        val curl = os.spawn(
          cmd = (
            "curl",
            "-sS",
            "-L",
            "--connect-timeout",
            "5",
            "--max-time",
            "15",
            ExampleResourcess.RemoteReadme.url
          ),
          stderr = os.Inherit
        )
        val gzip = os.spawn(cmd = ("gzip", "-n", "-6"), stdin = curl.stdout)
        val sha = os.spawn(cmd = ("shasum", "-a", "256"), stdin = gzip.stdout)
        sha.stdout.trim() ==> s"${ExampleResourcess.RemoteReadme.gzip6ShaSum256}  -"
      }
    }
    test("spawn callback") - prep { wd =>
      if (TestUtil.isInstalled("echo") && Unix()) {
        val output: mutable.Buffer[String] = mutable.Buffer()
        val sub = os.spawn(
          cmd = ("echo", "output"),
          stdout = ProcessOutput((bytes, count) => output += new String(bytes, 0, count))
        )
        val finished = sub.join(5000)
        sub.wrapped.getOutputStream().flush()
        assert(finished)
        assert(sub.exitCode() == 0)
        val expectedOutput = "output\n"
        val actualOutput = output.mkString("")
        assert(actualOutput == expectedOutput)
        sub.destroy()
      }
    }
    def tryLock(p: os.Path) = FileChannel
      .open(p.toNIO, util.EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE))
      .tryLock()
    def waitForLockTaken(p: os.Path) = {
      while ({
        val waitLock = tryLock(p)
        if (waitLock != null) {
          waitLock.release()
          true
        } else false
      }) Thread.sleep(1)
    }

    test("destroy") {
      if (Unix()) {
        try {
          val temp1 = os.temp()
          val sub1 = os.spawn((sys.env("TEST_SPAWN_EXIT_HOOK_ASSEMBLY"), temp1))
          waitForLockTaken(temp1)
          sub1.destroy()
          if (sub1.isAlive()) {
            throw new Exception(
              s"destroy: expected subprocess to be dead after synchronous destroy, temp: $temp1"
            )
          }

          val temp2 = os.temp()
          val sub2 = os.spawn((sys.env("TEST_SPAWN_EXIT_HOOK_ASSEMBLY"), temp2))
          waitForLockTaken(temp2)
          sub2.destroy(async = true)
          if (!sub2.isAlive()) {
            throw new Exception(
              s"destroy: expected subprocess to still be alive after async destroy, temp: $temp2"
            )
          }
        } catch {
          case ex: Exception =>
            // Enhanced error reporting for CI debugging
            throw new Exception(s"destroy test failed: ${ex.getMessage}", ex)
        }
      }
    }

    test("spawnExitHook") {
      test("destroyDefaultGrace") {
        if (Unix()) {
          val temp = os.temp()
          val lock0 = tryLock(temp)
          // file starts off not locked so can be taken and released
          assert(lock0 != null)
          lock0.release()

          val subprocess = os.spawn((sys.env("TEST_SPAWN_EXIT_HOOK_ASSEMBLY"), temp))
          waitForLockTaken(temp)

          subprocess.destroy()
          // after calling destroy on the subprocess, the transitive subprocess
          // should be killed by the exit hook, so the lock can now be taken
          val lock = tryLock(temp)
          assert(lock != null)
          lock.release()
        }
      }

      test("destroyNoGrace") - retry(5) {
        if (Unix()) {
          val temp = os.temp()
          try {
            val subprocess = os.spawn((sys.env("TEST_SPAWN_EXIT_HOOK_ASSEMBLY"), temp))
            waitForLockTaken(temp)

            subprocess.destroy(shutdownGracePeriod = 0)
            // this should fail since the subprocess is shut down forcibly without grace period
            // so there is no time for any exit hooks to run to shut down the transitive subprocess
            val lock = tryLock(temp)
            assert(lock == null)
          } catch {
            case ex: Throwable =>
              // Enhanced error reporting for CI debugging
              throw new Exception(s"destroyNoGrace failed: ${ex.getMessage}, temp file: $temp", ex)
          }
        }
      }

      test("infiniteGrace") {
        if (Unix()) {
          val temp = os.temp()
          val lock0 = tryLock(temp)
          // file starts off not locked so can be taken and released
          assert(lock0 != null)
          lock0.release()

          // Force the subprocess exit to stall for 500ms
          val subprocess = os.spawn((sys.env("TEST_SPAWN_EXIT_HOOK_ASSEMBLY"), temp, 500))
          waitForLockTaken(temp)

          val start = System.currentTimeMillis()
          subprocess.destroy(shutdownGracePeriod = -1)
          val end = System.currentTimeMillis()
          // Because we set the shutdownGracePeriod to -1, it takes more than 500ms to shutdown,
          // even though the default shutdown grace period is 100. But the sub-sub-process will
          // have been shut down by the time the sub-process exits, so the lock is available
          assert(end - start > 500)
          val lock = tryLock(temp)
          assert(lock != null)
        }
      }
    }
  }
}
