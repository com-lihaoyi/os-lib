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

          // Taking input from a file and directing output to another file
          os.proc("base64").call(stdin = wd / "File.txt", stdout = wd / "File.txt.b64")

          os.read(wd / "File.txt.b64") ==> "SSBhbSBjb3c=\n"

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
          val curl = os.proc("curl", "-L" , "https://git.io/fpfTs").spawn(stderr = os.Inherit)
          val gzip = os.proc("gzip", "-n").spawn(stdin = curl.stdout)
          val sha = os.proc("shasum", "-a", "256").spawn(stdin = gzip.stdout)
          sha.stdout.trim ==> "acc142175fa520a1cb2be5b97cbbe9bea092e8bba3fe2e95afa645615908229e  -"
        }
      }
    }
  }
}
