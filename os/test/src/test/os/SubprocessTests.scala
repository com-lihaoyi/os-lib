package test.os

import os._
import utest._

object SubprocessTests extends TestSuite{
  val scriptFolder = pwd/'os/'test/'resources/'test

  val lsCmd = if(scala.util.Properties.isWin) "dir" else "ls"

  val tests = Tests {
    'lines{
      val res = proc(lsCmd, scriptFolder).call()
      assert(
        res.out.lines.exists(_.contains("File.txt")),
        res.out.lines.exists(_.contains("folder1")),
        res.out.lines.exists(_.contains("folder2"))
      )
    }
    'string{
      val res = proc(lsCmd, scriptFolder).call()
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
        proc(lsCmd, pwd).call().out.string.contains("readme.md")
      )
    }
    'basicList{
      val files = List("readme.md", "build.sc")
      val output = proc(lsCmd, files).call().out.string
      assert(files.forall(output.contains))
    }
    'listMixAndMatch{
      val stuff = List("I", "am", "bovine")
      val result = proc('echo, "Hello,", stuff, "hear me roar").call()
      if(Unix())
        assert(result.out.string.contains("Hello, " + stuff.mkString(" ") + " hear me roar"))
      else // win quotes multiword args
        assert(result.out.string.contains("Hello, " + stuff.mkString(" ") + " \"hear me roar\""))
    }
    'failures{
      val ex = intercept[os.SubprocessException]{ proc(lsCmd, "does-not-exist").call(check = true) }
      val res: CommandResult = ex.result
      assert(
        res.exitCode != 0,
        res.err.string.contains("No such file or directory") || // unix
          res.err.string.contains("File Not Found") // win
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

    'envArgs{ if(Unix()){
      val res0 = proc('bash, "-c", "echo \"Hello$ENV_ARG\"").call(env = Map("ENV_ARG" -> "12"))
      assert(res0.out.lines == Seq("Hello12"))

      val res1 = proc('bash, "-c", "echo \"Hello$ENV_ARG\"").call(env = Map("ENV_ARG" -> "12"))
      assert(res1.out.lines == Seq("Hello12"))

      val res2 = proc('bash, "-c", "echo 'Hello$ENV_ARG'").call(env = Map("ENV_ARG" -> "12"))
      assert(res2.out.lines == Seq("Hello$ENV_ARG"))

      val res3 = proc('bash, "-c", "echo 'Hello'$ENV_ARG").call(env = Map("ENV_ARG" -> "123"))
      assert(res3.out.lines == Seq("Hello123"))
    }}
    'multiChunk {
      // Make sure that in the case where multiple chunks are being read from
      // the subprocess in quick succession, we ensure that the output handler
      // callbacks are properly ordered such that the output is aggregated
      // correctly
      'bashC{ if(TestUtil.isInstalled("python")) {
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
      }}
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
      val listed1 = proc(lsCmd).call(cwd = pwd)
      val listed2 = proc(lsCmd).call(cwd = pwd / up)

      assert(listed2 != listed1)
    }
    'customWorkingDir{
      val res1 = proc(lsCmd).call(cwd = pwd) // explicitly
      // or implicitly
      val res2 = proc(lsCmd).call()
    }

    'fileCustomWorkingDir - {
      if(Unix()){
        val output = proc(scriptFolder/'misc/'echo_with_wd, 'HELLO).call(cwd = root/'usr)
        assert(output.out.lines == Seq("HELLO /usr"))
      }
    }
  }
}
