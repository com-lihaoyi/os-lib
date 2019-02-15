package test.os

import java.io.{BufferedReader, ByteArrayInputStream, ByteArrayOutputStream, InputStreamReader}

import os._
import utest._

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

    'output - {
      // Make sure the os.SubProcess.OutputStream matches the behavior of java.io.BufferedReader
      // when run on tricky combinations of \r and \n
      def check(s: String) = {
        val stream1 = new os.SubProcess.OutputStream(new ByteArrayInputStream(s.getBytes), 2)
        val stream2 = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes())))
        def lines(f: () => String) = {
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
        val list1 = lines(stream1.readLine)
        val list2 = lines(stream2.readLine)
        val p = os.proc("cat").spawn()

        p.stdin.write(s)

        p.stdin.close()


        assert(list1 == list2)
        pprint.log(list1.map(_.toCharArray))
        val list3 = lines(p.stdout.readLine)
        pprint.log(list3.map(_.toCharArray))
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
      check("a\rc\rb")
      check("a\rc\nb")
      check("a\nc\rb")
      check("a\nc\nb")
    }
    'fuzz - {
      'inAndOut - {
        val random = new scala.util.Random(313373)

        val cat = proc('cat).spawn()

        for (n <- Range(0, 20000)) {
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
        for (n <- Range(0, 20000)) {
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
        /*
        @ val br = new java.io.BufferedReader(new java.io.StringReader("123\n456\r789\n\rabc\r\ndef"))
        br: java.io.BufferedReader = java.io.BufferedReader@2313052e

        @ br.readLine()
        res1: String = "123"

        @ br.readLine()
        res2: String = "456"

        @ br.readLine()
        res3: String = "789"

        @ br.readLine()
        res4: String = ""

        @ br.readLine()
        res5: String = "abc"
        */
        val random = new scala.util.Random(313373)

        val cat = proc('cat).spawn()
        val output = new StringBuilder()
        val input = new StringBuilder()
        val drainer = new Thread({() =>
          while({
            cat.stdout.readLine() match{
              case null => false
              case line =>
                output.append(line)
                output.append('\n')
                true
            }
          })()
        })

        drainer.start()
        for (n <- Range(0, 1)) {
          for(_ <- Range(0, n)){
            val c = random.nextPrintableChar()
            cat.stdin.write(c)
            input.append(c)
          }
          val newline = "\r"

          cat.stdin.write(newline)
          input.append(newline)
          cat.stdin.flush()
        }
        cat.stdin.close()
        drainer.join()
        val inputLines = input.toString.linesIterator
        val outputLines = output.toString.linesIterator
        while (inputLines.hasNext && outputLines.hasNext){
          val inputLine = inputLines.next
          val outputLine = outputLines.next

          if (inputLine != outputLine){
            throw new Exception(pprint.apply(inputLine) + "\n" + pprint.apply(outputLine))
          }
        }
        if (inputLines.hasNext || outputLines.hasNext){
          throw new Exception("inputLines and outputLines has unequal lengths")
        }
      }
    }
  }
}
