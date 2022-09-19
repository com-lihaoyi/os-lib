package test.os

import java.io.{BufferedReader, InputStreamReader}
import os.ProcessOutput

import scala.collection.mutable

import test.os.TestUtil.prep
import utest._

object SpawningSubprocessesTests extends TestSuite {

  def tests = Tests {
    test("proc") {
      test("call") {
        test - prep { wd =>
          if (Unix()) {
            val res = os.proc("ls", wd / "folder2").call()

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
              os.proc("ls", "doesnt-exist").call(cwd = wd)
            }

            assert(thrown.result.exitCode != 0)

            val fail = os.proc("ls", "doesnt-exist").call(cwd = wd, check = false, stderr = os.Pipe)

            assert(fail.exitCode != 0)

            fail.out.text() ==> ""

            assert(fail.err.text().contains("No such file or directory"))

            // You can pass in data to a subprocess' stdin
            val hash = os.proc("shasum", "-a", "256").call(stdin = "Hello World")
            hash.out.trim() ==> "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e  -"

            // Taking input from a file and directing output to another file
            os.proc("base64").call(stdin = wd / "File.txt", stdout = wd / "File.txt.b64")

            os.read(wd / "File.txt.b64") ==> "SSBhbSBjb3c=\n"

            if (false) {
              os.proc("vim").call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
            }
          }
        }
        test - prep { wd =>
          if (Unix()) {
            val ex = intercept[os.SubprocessException] {
              os.proc("bash", "-c", "echo 123; sleep 10; echo 456")
                .call(timeout = 2000)
            }

            ex.result.out.trim() ==> "123"
          }
        }
      }
      test("stream") {
        test - prep { wd =>
          if (Unix()) {
            var lineCount = 1
            os.proc("find", ".").call(
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
            os.proc("find", ".").call(
              cwd = wd,
              stdout = os.ProcessOutput.Readlines(line => lineCount += 1)
            )
            lineCount ==> 22
          }
        }
      }

      test("spawn") {
        test - prep { wd =>
          if (TestUtil.isInstalled("python") && Unix()) {
            // Start a long-lived python process which you can communicate with
            val sub = os.proc(
              "python",
              "-u",
              "-c",
              if (TestUtil.isPython3()) "while True: print(eval(input()))"
              else "while True: print(eval(raw_input()))"
            )
              .spawn(cwd = wd)

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

            // You can chain multiple subprocess' stdin/stdout together
            val curl = os.proc("curl", "-L", "https://git.io/fpfTs").spawn(stderr = os.Inherit)
            val gzip = os.proc("gzip", "-n").spawn(stdin = curl.stdout)
            val sha = os.proc("shasum", "-a", "256").spawn(stdin = gzip.stdout)
            sha.stdout.trim() ==> "acc142175fa520a1cb2be5b97cbbe9bea092e8bba3fe2e95afa645615908229e  -"
          }
        }
      }
      test("spawn callback") {
        test - prep { wd =>
          if (TestUtil.isInstalled("python") && Unix()) {
            val output: mutable.Buffer[String] = mutable.Buffer()
            val sub = os.proc("echo", "output")
              .spawn(stdout =
                ProcessOutput((bytes, count) => output += new String(bytes, 0, count))
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
      }
    }
  }
}
