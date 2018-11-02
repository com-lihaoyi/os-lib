package test.os

import java.nio.file.attribute.{GroupPrincipal, FileTime}

import utest._
import os.{RegexContextMaker, /}
object ExampleTests extends TestSuite{

  val tests = Tests {
    'splash{
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
    }
    'reference{
      // Let's pick our working directory
      val wd = os.pwd/'out/'example3

      // And make sure it's empty
      os.remove.all(wd)
      os.makeDir.all(wd)

      // Reading and writing to files is done through the `os.read` and
      // `os.write` You can write `Strings`, `Traversable[String]`s or `Array[Byte]`s
      os.write(wd/"file1.txt", "I am cow")
      os.write(wd/"file2.txt", Seq("I am cow\n", "hear me moo"))
      os.write(wd/'file3, "I weigh twice as much as you".getBytes)

      // When reading, you can either `os.read` a `String`, `os.read.lines` to
      // get a `Vector[String]` or `os.read.bytes` to get an `Array[Byte]`
      os.read(wd/"file1.txt")        ==> "I am cow"
      os.read(wd/"file2.txt")        ==> "I am cow\nhear me moo"
      os.read.lines(wd/"file2.txt")  ==> Vector("I am cow", "hear me moo")
      os.read.bytes(wd/"file3")      ==> "I weigh twice as much as you".getBytes

      // These operations are mirrored in `read.resource`,
      // `read.resource.lines` and `read.resource.bytes` to conveniently read
      // files from your classpath:
      val resourcePath = os.resource/'test/'os/'folder/"file.txt"
      os.read(resourcePath).length        ==> 18
      os.read.bytes(resourcePath).length  ==> 18
      os.read.lines(resourcePath).length  ==> 1

      // You can read resources relative to any particular class, including
      // the "current" class by passing in `getClass`
      val relResourcePath = os.resource(getClass)/'folder/"file.txt"
      os.read(relResourcePath).length        ==> 18
      os.read.bytes(relResourcePath).length  ==> 18
      os.read.lines(relResourcePath).length  ==> 1

      // You can also read `InputStream`s
      val inputStream = new java.io.ByteArrayInputStream(
        Array[Byte](104, 101, 108, 108, 111)
      )
      os.read(inputStream)           ==> "hello"

      // By default, `os.write` fails if there is already a file in place. Use
      // `write.append` or `write.over` if you want to append-to/overwrite
      // any existing files
      os.write.append(wd/"file1.txt", "\nI eat grass")
      os.write.over(wd/"file2.txt", "I am cow\nHere I stand")

      os.read(wd/"file1.txt")        ==> "I am cow\nI eat grass"
      os.read(wd/"file2.txt")        ==> "I am cow\nHere I stand"

      // You can create folders through `os.makeDir.all`. This behaves the same as
      // `mkdir -p` in Bash, and creates and parents necessary
      val deep = wd/'this/'is/'very/'deep
      os.makeDir.all(deep)
      // Writing to a file also creates necessary parents
      os.write(deep/'deeeep/"file.txt", "I am cow")

      // `os.list` provides a listing of every direct child of the given folder.
      // Both files and folders are included
      os.list(wd)    ==> Seq(wd/"file1.txt", wd/"file2.txt", wd/'file3, wd/'this)

      // `os.walk` does the same thing recursively
      os.walk(deep) ==> Seq(deep/'deeeep, deep/'deeeep/"file.txt")

      // You can move files or folders with `os.move` and remove them with `os.remove`
      os.list(deep)  ==> Seq(deep/'deeeep)
      os.move(deep/'deeeep, deep/'renamed_deeeep)
      os.list(deep)  ==> Seq(deep/'renamed_deeeep)

      // `os.move.into` lets you move a file into a
      // particular folder, rather than to particular path
      os.move.into(deep/'renamed_deeeep/"file.txt", deep)
      os.list(deep/'renamed_deeeep) ==> Seq()
      os.list(deep)  ==> Seq(deep/"file.txt", deep/'renamed_deeeep)

      // `os.move.over` lets you move a file to a particular path, but
      // if something was there before it stomps over it
      os.move.over(deep/"file.txt", deep/'renamed_deeeep)
      os.list(deep)  ==> Seq(deep/'renamed_deeeep)
      os.read(deep/'renamed_deeeep) ==> "I am cow" // contents from file.txt

      // `os.remove` can delete individual files or folders
      os.remove(deep/'renamed_deeeep)

      // while `os.remove.all` behaves the same as `rm -rf` in Bash, and deletes
      // anything: file, folder, even a folder filled with contents.
      os.remove.all(deep/'renamed_deeeep)
      os.list(deep)  ==> Seq()

      // You can stat paths to find out information about any file or
      // folder that exists there
      val info = os.stat(wd/"file1.txt")
      info.isDir  ==> false
      info.isFile ==> true
      info.size   ==> 20
      info.name   ==> "file1.txt"

      // Apart from `os.stat`, there are also methods to provide the individual
      // bits of information you want
      os.isDir(wd/"file1.txt")  ==> false
      os.isFile(wd/"file1.txt") ==> true
      os.size(wd/"file1.txt")   ==> 20

      // You can also use `os.stat.full` which provides more information
      val fullInfo = os.stat.full(wd/"file1.txt")
      fullInfo.ctime: FileTime
      fullInfo.atime: FileTime
      fullInfo.group: GroupPrincipal

      // `os.getPerms`/`os.setPerms` can be used to modify the filesystem
      // permissions of a file or folder, by passing in a permissions string:
      os.setPerms(wd/"file1.txt", "rwxrwxrwx")
      os.getPerms(wd/"file1.txt").toString() ==> "rwxrwxrwx"

      // or a permissions integer
      os.setPerms(wd/"file1.txt", Integer.parseInt("777", 8))
      os.getPerms(wd/"file1.txt").toInt() ==> Integer.parseInt("777", 8)

      // `os.getOwner`/`os.setOwner`/`os.getGroup`/`os.setGroup` let you
      // inspect and modify ownership of files and folders
      val owner = os.getOwner(wd/"file1.txt")
      os.setOwner(wd/"file1.txt", owner)
      val group = os.getGroup(wd/"file1.txt")
      os.setGroup(wd/"file1.txt", group)

      // `os.proc.call()` cal be used to spawn subprocesses, by passing in
      // a sequence of strings making up the subprocess command:
      val res = os.proc('echo, "abc").call()
      val listed = res.out.string
      assert(listed == "abc\n")

      // You can also pass in specific files you wish to invoke in the
      // subprocess, and customize it's environment, working directory, etc.
      val res2 = os.proc(os.root/'bin/'bash, "-c", "echo 'Hello'$ENV_ARG")
        .call(env = Map("ENV_ARG" -> "123"))

      assert(res2.out.string.trim == "Hello123")
      ()
    }
    'longExample{

      // Pick the directory you want to work with,
      // relative to the process working dir
      val wd = os.pwd/'out/'example2

      // Delete a file or folder, if it exists
      os.remove.all(wd)

      // Make a folder named "folder"
      os.makeDir.all(wd/'folder)

      // Copy a file or folder to a particular path
      os.copy(wd/'folder, wd/'folder1)
      // Copy a file or folder *into* another folder at a particular path
      // There's also `cp.over` which copies it to a path and stomps over
      // anything that was there before.
      os.copy.into(wd/'folder, wd/'folder1)


      // List the current directory
      val listed = os.list(wd)

      // Write to a file without pain! Necessary
      // enclosing directories are created automatically
      os.write(wd/'dir2/"file1.scala", "package example\nclass Foo{}\n")
      os.write(wd/'dir2/"file2.scala", "package example\nclass Bar{}\n")

      // Rename all .scala files inside the folder d into .java files
      os.list(wd/'dir2).map(os.move{case p/r"$x.scala" => p/r"$x.java"})

      // List files in a folder
      val renamed = os.list(wd/'dir2)

      // Line-count of all .java files recursively in wd
      val lineCount = os.walk(wd)
        .filter(_.ext == "java")
        .map(os.read.lines)
        .map(_.size)
        .sum

      // Find and concatenate all .java files directly in the working directory
      os.write(
        wd/'out/'scratch/"bundled.java",
        os.list(wd/'dir2).filter(_.ext == "java").map(os.read)
      )


      assert(
        listed == Seq(wd/'folder, wd/'folder1),
        os.list(wd/'folder1) == Seq(wd/'folder1/'folder),
        lineCount == 4,
        renamed == Seq(wd/'dir2/"file1.java", wd/'dir2/"file2.java"),
        os.read(wd/'out/'scratch/"bundled.java") ==
        "package example\nclass Foo{}\npackage example\nclass Bar{}\n"
      )


      os.write(wd/'py/"cow.scala", "Hello World")
      os.write(wd/".file", "Hello")
      // Chains

      // Move all files inside the "py" folder out of it
      os.list(wd/"py").map(os.move{case p/"py"/x => p/x })

      // Find all dot-files in the current folder
      val dots = os.list(wd).filter(_.last(0) == '.')

      // Find the names of the 10 largest files in the current working directory
      os.walk(wd).map(x => os.size(x) -> x).sortBy(-_._1).take(10)

      // Sorted list of the most common words in your .scala source files
      def txt = os.walk(wd).filter(_.ext == "scala").map(os.read)
      def freq(s: Seq[String]) = s groupBy (x => x) mapValues (_.length) toSeq
      val map = freq(txt.flatMap(_.split("[^a-zA-Z0-9_]"))).sortBy(-_._2)

      assert(
        os.list(wd).toSeq.contains(wd/"cow.scala"),
        dots == Seq(wd/".file"),
        map == Seq("Hello" -> 1, "World" -> 1)
      )
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
      val wd = pwd/'os/'test/'resources/'testdata

      // find . -name '*.txt' | xargs wc -l
      val lines = os.walk(wd)
        .filter(_.ext == "txt")
        .map(read.lines)
        .map(_.length)
        .sum

      assert(lines == 20)
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
