package test.os

import java.nio.file.NoSuchFileException
import java.nio.{file => nio}

import utest._
import os.{GlobSyntax, /}
import java.nio.charset.Charset

object OpTests extends TestSuite {

  val tests = Tests {
    val res = os.Path(sys.env("MILL_TEST_RESOURCE_FOLDER")) / "test"
    test("ls") - assert(
      os.list(res).toSet == Set(
        res / "folder1",
        res / "folder2",
        res / "misc",
        res / "os",
        res / "File.txt",
        res / "Multi Line.txt"
      ),
      os.list(res / "folder2").toSet == Set(
        res / "folder2" / "nestedA",
        res / "folder2" / "nestedB"
      )
    )
    test("rm") {
      // shouldn't crash
      os.remove.all(os.pwd / "out" / "scratch" / "nonexistent")
      // shouldn't crash
      os.remove(os.pwd / "out" / "scratch" / "nonexistent") ==> false

      // should crash
      intercept[NoSuchFileException] {
        os.remove(os.pwd / "out" / "scratch" / "nonexistent", checkExists = true)
      }
    }
    test("lsR") {
      os.walk(res).foreach(println)
      intercept[java.nio.file.NoSuchFileException](
        os.walk(os.pwd / "out" / "scratch" / "nonexistent")
      )
      assert(
        os.walk(res / "folder2" / "nestedB") == Seq(res / "folder2" / "nestedB" / "b.txt"),
        os.walk(res / "folder2").toSet == Set(
          res / "folder2" / "nestedA",
          res / "folder2" / "nestedA" / "a.txt",
          res / "folder2" / "nestedB",
          res / "folder2" / "nestedB" / "b.txt"
        )
      )
    }
    test("Mutating") {
      val testFolder = os.pwd / "out" / "scratch" / "test"
      os.remove.all(testFolder)
      os.makeDir.all(testFolder)
      test("cp") {
        val d = testFolder / "copying"
        test("basic") {
          assert(
            !os.exists(d / "folder"),
            !os.exists(d / "file")
          )
          os.makeDir.all(d / "folder")
          os.write(d / "file", "omg")
          assert(
            os.exists(d / "folder"),
            os.exists(d / "file"),
            os.read(d / "file") == "omg"
          )
          os.copy(d / "folder", d / "folder2")
          os.copy(d / "file", d / "file2")

          assert(
            os.exists(d / "folder"),
            os.exists(d / "file"),
            os.read(d / "file") == "omg",
            os.exists(d / "folder2"),
            os.exists(d / "file2"),
            os.read(d / "file2") == "omg"
          )
        }
        test("deep") {
          os.write(d / "folderA" / "folderB" / "file", "Cow", createFolders = true)
          os.copy(d / "folderA", d / "folderC")
          assert(os.read(d / "folderC" / "folderB" / "file") == "Cow")
        }
        test("merging") {
          val mergeDir = d / "merge"
          os.write(mergeDir / "folderA" / "folderB" / "file", "Cow", createFolders = true)
          os.write(mergeDir / "folderC" / "file", "moo", createFolders = true)
          os.copy(mergeDir / "folderA", mergeDir / "folderC", mergeFolders = true)
          assert(os.read(mergeDir / "folderC" / "folderB" / "file") == "Cow")
          assert(os.read(mergeDir / "folderC" / "file") == "moo")
        }
      }
      test("mv") {
        test("basic") {
          val d = testFolder / "moving"
          os.makeDir.all(d / "folder")
          assert(os.list(d) == Seq(d / "folder"))
          os.move(d / "folder", d / "folder2")
          assert(os.list(d) == Seq(d / "folder2"))
        }
        test("shallow") {
          val d = testFolder / "moving2"
          os.makeDir(d)
          os.write(d / "A.scala", "AScala")
          os.write(d / "B.scala", "BScala")
          os.write(d / "A.py", "APy")
          os.write(d / "B.py", "BPy")
          def fileSet = os.list(d).map(_.last).toSet
          assert(fileSet == Set("A.scala", "B.scala", "A.py", "B.py"))
          test("partialMoves") {
            os.list(d).collect(os.move.matching { case p / g"$x.scala" => p / g"$x.java" })
            assert(fileSet == Set("A.java", "B.java", "A.py", "B.py"))
            os.list(d).collect(os.move.matching { case p / g"A.$x" => p / g"C.$x" })
            assert(fileSet == Set("C.java", "B.java", "C.py", "B.py"))
          }
          test("fullMoves") {
            os.list(d).map(os.move.matching { case p / g"$x.$y" => p / g"$y.$x" })
            assert(fileSet == Set("scala.A", "scala.B", "py.A", "py.B"))
            def die = os.list(d).map(os.move.matching { case p / g"A.$x" => p / g"C.$x" })
            intercept[MatchError] { die }
          }
        }

        test("deep") {
          val d = testFolder / "moving2"
          os.makeDir(d)
          os.makeDir(d / "scala")
          os.write(d / "scala" / "A", "AScala")
          os.write(d / "scala" / "B", "BScala")
          os.makeDir(d / "py")
          os.write(d / "py" / "A", "APy")
          os.write(d / "py" / "B", "BPy")
          test("partialMoves") {
            os.walk(d).collect(os.move.matching { case d / "py" / x => d / x })
            assert(
              os.walk(d).toSet == Set(
                d / "py",
                d / "scala",
                d / "scala" / "A",
                d / "scala" / "B",
                d / "A",
                d / "B"
              )
            )
          }
          test("fullMoves") {
            def die = os.walk(d).map(os.move.matching { case d / "py" / x => d / x })
            intercept[MatchError] { die }

            os.walk(d).filter(os.isFile).map(os.move.matching {
              case d / "py" / x => d / "scala" / "py" / x
              case d / "scala" / x => d / "py" / "scala" / x
              case d => println("NOT FOUND " + d); d
            })

            assert(
              os.walk(d).toSet == Set(
                d / "py",
                d / "scala",
                d / "py" / "scala",
                d / "scala" / "py",
                d / "scala" / "py" / "A",
                d / "scala" / "py" / "B",
                d / "py" / "scala" / "A",
                d / "py" / "scala" / "B"
              )
            )
          }
        }
        //          ls! wd | mv*
      }

      test("mkdirRm") {
        test("singleFolder") {
          val single = testFolder / "single"
          os.makeDir.all(single / "inner")
          assert(os.list(single) == Seq(single / "inner"))
          os.remove(single / "inner")
          assert(os.list(single) == Seq())
        }
        test("nestedFolders") {
          val nested = testFolder / "nested"
          os.makeDir.all(nested / "inner" / "innerer" / "innerest")
          assert(
            os.list(nested) == Seq(nested / "inner"),
            os.list(nested / "inner") == Seq(nested / "inner" / "innerer"),
            os.list(nested / "inner" / "innerer") == Seq(nested / "inner" / "innerer" / "innerest")
          )
          os.remove.all(nested / "inner")
          assert(os.list(nested) == Seq())
        }
      }

      test("readWrite") {
        val d = testFolder / "readWrite"
        os.makeDir.all(d)
        test("simple") {
          os.write(d / "file", "i am a cow")
          assert(os.read(d / "file") == "i am a cow")
        }
        test("autoMkdir") {
          os.write(d / "folder" / "folder" / "file", "i am a cow", createFolders = true)
          assert(os.read(d / "folder" / "folder" / "file") == "i am a cow")
        }
        test("binary") {
          os.write(d / "file", Array[Byte](1, 2, 3, 4))
          assert(os.read(d / "file").toSeq == Array[Byte](1, 2, 3, 4).toSeq)
        }
        test("concatenating") {
          os.write(d / "concat1", Seq("a", "b", "c"))
          assert(os.read(d / "concat1") == "abc")
          os.write(d / "concat2", Seq(Array[Byte](1, 2), Array[Byte](3, 4)))
          assert(os.read.bytes(d / "concat2").toSeq == Array[Byte](1, 2, 3, 4).toSeq)
          os.write(d / "concat3", geny.Generator(Array[Byte](1, 2), Array[Byte](3, 4)))
          assert(os.read.bytes(d / "concat3").toSeq == Array[Byte](1, 2, 3, 4).toSeq)
        }
        test("writeAppend") {
          os.write.append(d / "append.txt", "Hello")
          assert(os.read(d / "append.txt") == "Hello")
          os.write.append(d / "append.txt", " World")
          assert(os.read(d / "append.txt") == "Hello World")
        }
        test("writeOver") {
          os.write.over(d / "append.txt", "Hello")
          assert(os.read(d / "append.txt") == "Hello")
          os.write.over(d / "append.txt", " Wor")
          assert(os.read(d / "append.txt") == " Wor")
        }

      }

      test("Failures") {
        val d = testFolder / "failures"
        os.makeDir.all(d)
        test("nonexistant") {

          test - intercept[nio.NoSuchFileException](os.read(d / "nonexistent"))
          test - intercept[nio.NoSuchFileException](os.copy(d / "nonexistent", d / "yolo"))
          test - intercept[nio.NoSuchFileException](os.move(d / "nonexistent", d / "yolo"))
        }
        test("collisions") {
          os.makeDir.all(d / "folder")
          os.write(d / "file", "lolol")
          test - intercept[nio.FileAlreadyExistsException](os.move(d / "file", d / "folder"))
          test - intercept[nio.FileAlreadyExistsException](os.copy(d / "file", d / "folder"))
          test - intercept[nio.FileAlreadyExistsException](os.write(d / "file", "lols"))
        }
      }
    }

  }
}
