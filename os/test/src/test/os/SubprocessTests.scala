package test.os

import os._
import utest._

object SubprocessTests extends TestSuite{
  val scriptFolder = pwd/'os/'test/'resources/'scripts

  val tests = Tests {
    'implicitWd{
      'lines{
        val res = proc('ls, "os/test/resources/testdata").call()
        assert(res.out.lines == Seq("File.txt", "folder1", "folder2"))
      }
      'string{
        val res = proc('ls, "os/test/resources/testdata").call()
        assert(res.out.string == "File.txt\nfolder1\nfolder2\n")
      }
      'bytes{
        if(Unix()){
          val res = proc('echo, "abc").call()
          val listed = res.out.bytes
          //        assert(listed == "File.txt\nfolder\nfolder2\nFile.txt".getBytes)
          listed.toSeq
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
        val ex = intercept[ShelloutException]{ proc('ls, "does-not-exist").call(check = true) }
        val res: CommandResult = ex.result
        assert(
          res.exitCode != 0,
          res.err.string.contains("No such file or directory")
        )
      }

      'filebased{
        if(Unix()){
          assert(proc(scriptFolder/'echo, 'HELLO).call().out.lines.mkString == "HELLO")

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
        val output = proc(scriptFolder/'echo_with_wd, 'HELLO).call(cwd = root/'usr)
        assert(output.out.lines == Seq("HELLO /usr"))
      }
    }
  }
}
