package test.os

import java.nio.file.attribute.{GroupPrincipal, FileTime}

import utest._
object ExampleTests extends TestSuite{

  val tests = Tests {
    'splash - TestUtil.prep{wd =>
      // Make sure working directory exists and is empty
      val wd = os.pwd/"out"/"splash"
      os.remove.all(wd)
      os.makeDir.all(wd)

      os.write(wd/"file.txt", "hello")
      os.read(wd/"file.txt") ==> "hello"

      os.copy(wd/"file.txt", wd/"copied.txt")
      os.list(wd) ==> Seq(wd/"copied.txt", wd/"file.txt")

      val invoked = os.proc("cat", wd/"file.txt", wd/"copied.txt").call(cwd = wd)
      invoked.out.trim ==> "hellohello"

      val curl = os.proc("curl", "-L" , "https://git.io/fpvpS").spawn(stderr = os.Inherit)
      val gzip = os.proc("gzip", "-n").spawn(stdin = curl.stdout)
      val sha = os.proc("shasum", "-a", "256").spawn(stdin = gzip.stdout)
      sha.stdout.trim ==> "acc142175fa520a1cb2be5b97cbbe9bea092e8bba3fe2e95afa645615908229e  -"
    }

    'concatTxt - TestUtil.prep{wd =>
      // Find and concatenate all .txt files directly in the working directory
      os.write(
        wd/"all.txt",
        os.list(wd).filter(_.ext == "txt").map(os.read)
      )

      os.read(wd/"all.txt") ==>
        """I am cowI am cow
          |Hear me moo
          |I weigh twice as much as you
          |And I look good on the barbecue""".stripMargin
    }

    'lineCount - TestUtil.prep{wd =>
      // Line-count of all .txt files recursively in wd
      val lineCount = os.walk(wd)
        .filter(_.ext == "txt")
        .map(os.read.lines)
        .map(_.size)
        .sum

      lineCount ==> 9
    }

    'largestThree - TestUtil.prep{ wd =>
      // Find the largest three files in the given folder tree
      val largestThree = os.walk(wd)
        .filter(os.isFile(_, followLinks = false))
        .map(x => os.size(x) -> x).sortBy(-_._1)
        .take(3)

      largestThree ==> Seq(
        (711, wd / "misc" / "binary.png"),
        (81, wd / "Multi Line.txt"),
        (22, wd / "folder1" / "one.txt")
      )
    }

    'moveOut - TestUtil.prep{ wd =>
      // Move all files inside the "misc" folder out of it
      import os.{GlobSyntax, /}
      os.list(wd/"misc").map(os.move.matching{case p/"misc"/x => p/x })
    }

    'frequency - TestUtil.prep{ wd =>
      // Calculate the word frequency of all the text files in the folder tree
      def txt = os.walk(wd).filter(_.ext == "txt").map(os.read)
      def freq(s: Seq[String]) = s groupBy (x => x) mapValues (_.length) toSeq
      val map = freq(txt.flatMap(_.split("[^a-zA-Z0-9_]"))).sortBy(-_._2)
      map
    }
    'comparison{
      import os._
      os.remove.all(pwd/'out/'scratch/'folder/'thing/'file)
      write(pwd/'out/'scratch/'folder/'thing/'file, "Hello!")

      def removeAll(path: String) = {
        def getRecursively(f: java.io.File): Seq[java.io.File] = {
          f.listFiles.filter(_.isDirectory).flatMap(getRecursively) ++ f.listFiles
        }
        getRecursively(new java.io.File(path)).foreach{f =>
          println(f)
          if (!f.delete())
            throw new RuntimeException("Failed to delete " + f.getAbsolutePath)
        }
        new java.io.File(path).delete
      }
      removeAll("out/scratch/folder/thing")

      assert(os.list(pwd/'out/'scratch/'folder).toSeq == Nil)

      write(pwd/'out/'scratch/'folder/'thing/'file, "Hello!")

      os.remove.all(pwd/'out/'scratch/'folder/'thing)
      assert(os.list(pwd/'out/'scratch/'folder).toSeq == Nil)
    }

    'constructingPaths{
      import os._
      // Get the process' Current Working Directory. As a convention
      // the directory that "this" code cares about (which may differ
      // from the pwd) is called `wd`
      val wd = pwd

      // A path nested inside `wd`
      wd/'folder/'file

      // A path starting from the root
      root/'folder/'file

      // A path with spaces or other special characters
      wd/"My Folder"/"My File.txt"

      // Up one level from the wd
      wd/up

      // Up two levels from the wd
      wd/up/up
    }
    'newPath{
      import os._
      val target = pwd/'out/'scratch
    }
    'relPaths{
      import os._
      // The path "folder/file"
      val rel1 = rel/'folder/'file
      val rel2 = rel/'folder/'file

      // The path "file"; will get converted to a RelPath by an implicit
      val rel3 = 'file

      // The relative difference between two paths
      val target = pwd/'out/'scratch/'file
      assert((target relativeTo pwd) == rel/'out/'scratch/'file)

      // `up`s get resolved automatically
      val minus = pwd relativeTo target
      val ups = up/up/up
      assert(minus == ups)
      rel1: RelPath
      rel2: RelPath
      rel3: RelPath
    }
    'relPathCombine{
      import os._
      val target = pwd/'out/'scratch/'file
      val rel = target relativeTo pwd
      val newBase = root/'code/'server
      assert(newBase/rel == root/'code/'server/'out/'scratch/'file)
    }
    'relPathUp{
      import os._
      val target = root/'out/'scratch/'file
      assert(target/up == root/'out/'scratch)
    }
    'canonical - {if (Unix()){
      import os._
      assert((root/'folder/'file/up).toString == "/folder")
      // not "/folder/file/.."

      assert((rel/'folder/'file/up).toString == "folder")
      // not "folder/file/.."
    }}
    'findWc{
      import os._
      val wd = pwd/'os/'test/'resources/'test

      // find . -name '*.txt' | xargs wc -l
      val lines = os.walk(wd)
        .filter(_.ext == "txt")
        .map(read.lines)
        .map(_.length)
        .sum

      assert(lines == 9)
    }
    'addUpScalaSize{
      os.walk(os.pwd).filter(_.ext == "scala").map(os.size).reduce(_ + _)
    }
    'concatAll{if (Unix()){
      os.write.over(
        os.pwd/'out/'scratch/'test/"omg.txt",
        os.walk(os.pwd).filter(_.ext == "scala").map(os.read)
      )
    }}

    'noLongLines{
      import os._
      // Ensure that we don't have any Scala files in the current working directory
      // which have lines more than 100 characters long, excluding generated sources
      // in `src_managed` folders.

      def longLines(p: Path) =
        (p, read.lines(p).zipWithIndex.filter(_._1.length > 100).map(_._2))

      val filesWithTooLongLines =
        proc("git", "ls-files").call(cwd = os.pwd).out.lines
            .map(Path(_, os.pwd))
            .filter(_.ext == "scala")
            .map(longLines)
            .filter(_._2.length > 0)
            .filter(!_._1.segments.contains("src_managed"))

      assert(filesWithTooLongLines.length == 0)
    }
    'rename{
//      val d1/"omg"/x1 = wd
//      val d2/"omg"/x2 = wd
//      ls! wd |? (_.ext == "scala") | (x => mv! x ! x.pref)
    }
    'allSubpathsResolveCorrectly{
      import os._
      for(abs <- os.walk(pwd)){
        val rel = abs.relativeTo(pwd)
        assert(rel.ups == 0)
        assert(pwd / rel == abs)
      }
    }
  }
}
