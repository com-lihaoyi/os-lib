package test.os

import java.io._
import java.nio.charset.StandardCharsets

import os._
import utest._

import scala.collection.mutable

object SubprocessTests extends TestSuite{
  val scriptFolder = pwd/'os/'test/'resources/'test

  val tests = Tests {
    'lines{
      val res = proc('ls, "os/test/resources/test").call()
      assert(
        res.out.lines.contains("File.txt"),
        res.out.lines.contains("folder1"),
        res.out.lines.contains("folder2")
      )
    }
    'string{
      val res = proc('ls, "os/test/resources/test").call()
      assert(
        res.out.string.contains("File.txt"),
        res.out.string.contains("folder1"),
        res.out.string.contains("folder2")
      )
    }
    'bytes{
      if(Unix()){
        val res = proc(scriptFolder / 'misc / 'echo, "abc").call()
        val listed = res.out.bytes
        listed ==> "abc\n".getBytes
      }
    }
    'chained{
      assert(
        proc('git, 'init).call().out.string.contains("Reinitialized existing Git repository"),
        proc('git, "init").call().out.string.contains("Reinitialized existing Git repository"),
        proc('ls, pwd).call().out.string.contains("readme.md")
      )
    }
    'basicList{
      val files = List("readme.md", "build.sc")
      val output = proc('ls, files).call().out.string
      assert(files.forall(output.contains))
    }
    'listMixAndMatch{
      val stuff = List("I", "am", "bovine")
      val result = proc('echo, "Hello,", stuff, "hear me roar").call()
      assert(result.out.string.contains("Hello, " + stuff.mkString(" ") + " hear me roar"))
    }
    'failures{
      val ex = intercept[os.SubprocessException]{ proc('ls, "does-not-exist").call(check = true) }
      val res: CommandResult = ex.result
      assert(
        res.exitCode != 0,
        res.err.string.contains("No such file or directory")
      )
    }

    'filebased{
      if(Unix()){
        assert(proc(scriptFolder/'misc/'echo, 'HELLO).call().out.lines.mkString == "HELLO")

        val res: CommandResult =
          proc(root/'bin/'bash, "-c", "echo 'Hello'$ENV_ARG").call(
            env = Map("ENV_ARG" -> "123")
          )

        assert(res.out.string.trim == "Hello123")
      }
    }
    'filebased2{
      if(Unix()){
        val res = proc('which, 'echo).call()
        val echoRoot = Path(res.out.string.trim)
        assert(echoRoot == root/'bin/'echo)

        assert(proc(echoRoot, 'HELLO).call().out.lines == Seq("HELLO"))
      }
    }

    'envArgs{
      val res0 = proc('bash, "-c", "echo \"Hello$ENV_ARG\"").call(env = Map("ENV_ARG" -> "12"))
      assert(res0.out.lines == Seq("Hello12"))

      val res1 = proc('bash, "-c", "echo \"Hello$ENV_ARG\"").call(env = Map("ENV_ARG" -> "12"))
      assert(res1.out.lines == Seq("Hello12"))

      val res2 = proc('bash, "-c", "echo 'Hello$ENV_ARG'").call(env = Map("ENV_ARG" -> "12"))
      assert(res2.out.lines == Seq("Hello$ENV_ARG"))

      val res3 = proc('bash, "-c", "echo 'Hello'$ENV_ARG").call(env = Map("ENV_ARG" -> "123"))
      assert(res3.out.lines == Seq("Hello123"))
    }
    'multiChunk {
      // Make sure that in the case where multiple chunks are being read from
      // the subprocess in quick succession, we ensure that the output handler
      // callbacks are properly ordered such that the output is aggregated
      // correctly
      'bashC{
        os.proc('python, "-c",
        """import sys, time
          |for i in range(5):
          |  for j in range(10):
          |    sys.stdout.write(str(j))
          |    # Make sure it comes as multiple chunks, but close together!
          |    # Vary how close they are together to try and trigger race conditions
          |    time.sleep(0.00001 * i)
          |    sys.stdout.flush()
        """.stripMargin).call().out.string ==>
          "01234567890123456789012345678901234567890123456789"
      }
      'jarTf {
        // This was the original repro for the multi-chunk concurrency bugs
        val jarFile = os.pwd / 'os / 'test / 'resources / 'misc / "out.jar"
        os.proc('jar, "-tf", jarFile).call().out.string ==>
          """META-INF/MANIFEST.MF
            |test/FooTwo.class
            |test/Bar.class
            |test/BarTwo.class
            |test/Foo.class
            |test/BarThree.class
            |hello.txt
            |""".stripMargin
      }
    }
    'workingDirectory{
      val listed1 = proc('ls).call(cwd = pwd)
      val listed2 = proc('ls).call(cwd = pwd / up)

      assert(listed2 != listed1)
    }
    'customWorkingDir{
      val res1 = proc("ls").call(cwd = pwd) // explicitly
      // or implicitly
      val res2 = proc("ls").call()
    }

    'fileCustomWorkingDir - {
      if(Unix()){
        val output = proc(scriptFolder/'misc/'echo_with_wd, 'HELLO).call(cwd = root/'usr)
        assert(output.out.lines == Seq("HELLO /usr"))
      }
    }
    def readUntilNull(f: () => String) = {
      val list = collection.mutable.Buffer.empty[String]
      while({
        f() match{
          case null => false
          case s =>
            list.append(s)
            true
        }
      })()
      list
    }
    'output - {
      // Make sure the os.SubProcess.OutputStream matches the behavior of
      // java.io.BufferedReader, when run on tricky combinations of \r and \n
      def check(s: String) = {
        val stream1 = new BufferedReader(new InputStreamReader(
          new ByteArrayInputStream(s.getBytes())
        ))

        val list1 = readUntilNull(stream1.readLine)
        for(bufferSize <- Range.inclusive(1, 10)){
          val stream2 = new os.SubProcess.OutputStream(
            new ByteArrayInputStream(s.getBytes),
            bufferSize
          )
          val list2 = readUntilNull(stream2.readLine)
          assert(list1 == list2)
        }

        val p = os.proc("cat").spawn()
        p.stdin.write(s)
        p.stdin.close()

        val list3 = readUntilNull(p.stdout.readLine)
        assert(list1 == list3)
      }
      check("\r")
      check("\r\r")
      check("\r\n")
      check("\n\r")
      check("\n\n")
      check("\n")

      check("a\r")
      check("a\r\r")
      check("a\r\n")
      check("a\n\r")
      check("a\n\n")
      check("a\n")

      check("\rb")
      check("\r\rb")
      check("\r\nb")
      check("\n\rb")
      check("\n\nb")
      check("\nb")

      check("a\rb")
      check("a\r\rb")
      check("a\r\nb")
      check("a\n\rb")
      check("a\n\nb")
      check("a\nb")


      check("a\nc\rb")
      check("a\nc\r\rb")
      check("a\nc\nb")
      check("a\nc\n\nb")
      check("a\nc\r\nb")
      check("a\nc\n\rb")

      check("a\rc\rb")
      check("a\rc\r\rb")
      check("a\rc\nb")
      check("a\rc\n\nb")
      check("a\rc\r\nb")
      check("a\rc\n\rb")

      check("a\n\rc\rb")
      check("a\n\rc\r\rb")
      check("a\n\rc\nb")
      check("a\n\rc\n\nb")
      check("a\n\rc\r\nb")
      check("a\n\rc\n\rb")

      check("a\r\nc\rb")
      check("a\r\nc\r\rb")
      check("a\r\nc\nb")
      check("a\r\nc\n\nb")
      check("a\r\nc\r\nb")
      check("a\r\nc\n\rb")


      check("\na\r")
      check("\na\r\r")
      check("\na\r\n")
      check("\na\n\r")
      check("\na\n\n")
      check("\na\n")

      check("\ra\r")
      check("\ra\r\r")
      check("\ra\r\n")
      check("\ra\n\r")
      check("\ra\n\n")
      check("\ra\n")

      check("\n\ra\r")
      check("\n\ra\r\r")
      check("\n\ra\r\n")
      check("\n\ra\n\r")
      check("\n\ra\n\n")
      check("\n\ra\n")

      check("\r\na\r")
      check("\r\na\r\r")
      check("\r\na\r\n")
      check("\r\na\n\r")
      check("\r\na\n\n")
      check("\r\na\n")
    }
    'fuzz - {
      'inAndOut - {
        val random = new scala.util.Random(313373)

        val cat = proc('cat).spawn()

        for (n0 <- Range(0, 20000)) {
          val n = random.nextInt(n0 + 1)
          val chunk = new Array[Byte](n)
          random.nextBytes(chunk)
          cat.stdin.write(chunk)
          cat.stdin.flush()
          val out = new Array[Byte](n)
          cat.stdout.readFully(out)

          assert {
            identity(n)
            java.util.Arrays.equals(chunk, out)
          }
        }
        cat.stdin.close()
      }
      'uneven - {
        val random = new scala.util.Random(313373)

        val cat = proc('cat).spawn()
        val output = new ByteArrayOutputStream()
        val input = new ByteArrayOutputStream()
        val drainer = new Thread({() =>
          val readBuffer = new Array[Byte](1337)
          while({
            cat.stdout.read(readBuffer) match{
              case -1 => false
              case n =>
                output.write(readBuffer, 0, n)
                true
            }
          })()
        })

        drainer.start()
        for (n0 <- Range(0, 20000)) {
          val n = random.nextInt(n0 + 1)
          val chunk = new Array[Byte](n)
          random.nextBytes(chunk)
          cat.stdin.write(chunk)
          cat.stdin.flush()
          input.write(chunk)
        }
        cat.stdin.close()
        drainer.join()
        assert(java.util.Arrays.equals(input.toByteArray, output.toByteArray))
      }
      'lines - {
        val random = new scala.util.Random(313373)

        val cat = proc('cat).spawn()
        val output = mutable.Buffer.empty[String]
        val input = new ByteArrayOutputStream()
        val drainer = new Thread({() =>
          while({
            cat.stdout.readLine(StandardCharsets.UTF_8) match{
              case null => false
              case line =>
                output.append(line)
                true
            }
          })()
        })

        drainer.start()
        for (n <- Range(0, 5000)) {
          for(_ <- Range(0, random.nextInt(n + 1))){
            val c = random.nextInt()
            cat.stdin.write(c)
            input.write(c)
          }
          val newline = random.nextInt(4) match{
            case 0 => "\n"
            case 1 => "\r"
            case 2 => "\r\n"
            case 3 => "\n\r"
          }

          cat.stdin.write(newline)
          input.write(newline.getBytes)
          cat.stdin.flush()
        }

        val inputReader = new BufferedReader(
          new InputStreamReader(
            new ByteArrayInputStream(
              input.toByteArray
            ),
            StandardCharsets.UTF_8
          )
        )
        val inputLines = readUntilNull(inputReader.readLine)

        cat.stdin.close()
        drainer.join()

        assert(inputLines == output)
      }
    }
  }
}
