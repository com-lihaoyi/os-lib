package test.os

import java.io.{BufferedReader, InputStreamReader}

import test.os.TestUtil.prep
import utest._

object SpawningSubprocessesTests extends TestSuite {
  def tests = Tests{
    'proc - {
      'call - {
        * - prep { wd =>
          val res = os.proc('ls, wd/"folder2").call()

          res.exitCode ==> 0

          res.out.string ==>
            """nestedA
              |nestedB
              |""".stripMargin

          res.out.trim ==>
            """nestedA
              |nestedB""".stripMargin

          res.out.lines ==> Seq(
            "nestedA",
            "nestedB"
          )

          res.out.bytes


          val thrown = intercept[os.SubprocessException]{
            os.proc('ls, "doesnt-exist").call(cwd = wd)
          }

          assert(thrown.result.exitCode != 0)

          val fail = os.proc('ls, "doesnt-exist").call(cwd = wd, check = false)

          assert(fail.exitCode != 0)

          fail.out.string ==> ""

          assert(fail.err.string.contains("No such file or directory"))

          // You can pass in data to a subprocess' stdin
          val hash = os.proc("shasum", "-a", "256").call(stdin = "Hello World")
          hash.out.trim ==> "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e  -"

          if (false){
            os.proc("vim").call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
          }
        }
      }
      'stream - {
        * - prep { wd =>
          var lineCount = 1
          os.proc('find, ".").stream(
            cwd = wd,
            onOut = (buf, len) => lineCount += buf.slice(0, len).count(_ == '\n'),
            onErr = (buf, len) => () // do nothing
          )
          lineCount ==> 21
        }
      }
      'spawn - {
        * - prep { wd =>
          val sub = os.proc("python", "-c", "print eval(raw_input())").spawn(cwd = wd)
          sub.stdin.write("1 + 2")
          sub.stdin.writeLine("+ 4")
          sub.stdin.flush()
          sub.stdout.readLine() ==> "7"

          val sub2 = os.proc("python", "-c", "print eval(raw_input())").spawn(cwd = wd)
          sub2.stdin.write("1 + 2".getBytes)
          sub2.stdin.write("+ 4\n".getBytes)
          sub2.stdin.flush()
          sub2.stdout.read() ==> '7'.toByte

          // You can chain multiple subprocess' stdin/stdout together
          val tar = os.proc("tar", "cvf", "-", "os/test/resources/misc").spawn(stderr = os.Inherit)
          val gzip = os.proc("gzip", "-n").spawn(stdin = tar.stdout)
          val sha = os.proc("shasum", "-a", "256").spawn(stdin = gzip.stdout)
          sha.stdout.trim ==> "f696682bcb1b749deec20d10cf0e12b9f6473021a2846fc435c1e5a4b284cf3c  -"
        }
      }
    }
  }
}
