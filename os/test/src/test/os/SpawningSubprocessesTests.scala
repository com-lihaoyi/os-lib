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

          fail.exitCode ==> 1

          fail.out.string ==> ""

          assert(fail.err.string.contains("No such file or directory"))
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
          val out = new BufferedReader(new InputStreamReader(sub.getInputStream))
          sub.getOutputStream.write("1 + 2 + 3\n".getBytes)
          sub.getOutputStream.flush()
          out.readLine() ==> "6"
        }
      }
    }
  }
}
