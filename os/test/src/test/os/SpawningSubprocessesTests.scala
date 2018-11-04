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


          val fail = os.proc('ls, "doesnt-exist").call(cwd = wd)

          assert(fail.exitCode != 0)

          fail.out.string ==> ""

          assert(fail.err.string.contains("No such file or directory"))

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
        }
      }
    }
  }
}
