package test.os

import java.nio.file.NoSuchFileException
import java.nio.{file => nio}


import utest._
import os.{RegexContextMaker, /}
object OpTests extends TestSuite{

  val tests = Tests {
    val res = os.pwd/'os/'test/'resources/'testdata
    'ls - assert(
      os.list(res).toSet == Set(res/'folder1, res/'folder2, res/"File.txt"),
      os.list(res/'folder2).toSet == Set(
        res/'folder2/'folder2a,
        res/'folder2/'folder2b
      )

//      os.list(res/'folder2/'folder2b) == Seq()
    )
    'lsR{
      os.list.rec(res).foreach(println)
      intercept[java.nio.file.NoSuchFileException](os.list.rec(os.pwd/'out/'scratch/'nonexistent))
      assert(
        os.list.rec(res/'folder2/'folder2b) == Seq(res/'folder2/'folder2b/"b.txt"),
        os.list.rec(res/'folder2) == Seq(
          res/'folder2/'folder2a,
          res/'folder2/'folder2b,
          res/'folder2/'folder2a/"I am.txt",
          res/'folder2/'folder2b/"b.txt"
        ),
        os.list.rec(res) == Seq(
          res/"File.txt",
          res/'folder1,
          res/'folder2,
          res/'folder1/"Yoghurt Curds Cream Cheese.txt",
          res/'folder2/'folder2a,
          res/'folder2/'folder2b,
          res/'folder2/'folder2a/"I am.txt",
          res/'folder2/'folder2b/"b.txt"
        )
      )
    }
    'lsRecPermissions{
      if(Unix()){
        assert(os.list.rec(os.root/'var/'run).nonEmpty)
      }
    }
    'readResource{
      'positive{
        'absolute{
          val contents = os.read(os.resource/'test/'os/'folder/"file.txt")
          assert(contents.contains("file contents lols"))

          val cl = getClass.getClassLoader
          val contents2 = os.read(os.resource(cl)/'test/'os/'folder/"file.txt")
          assert(contents2.contains("file contents lols"))
        }

        'relative {
          val cls = classOf[test.os.Testing]
          val contents = os.read(os.resource(cls)/'folder/"file.txt")
          assert(contents.contains("file contents lols"))

          val contents2 = os.read(os.resource(getClass)/'folder/"file.txt")
          assert(contents2.contains("file contents lols"))
        }
      }
      'negative{
        * - intercept[os.ResourceNotFoundException]{
          os.read(os.resource/'folder/"file.txt")
        }

        * - intercept[os.ResourceNotFoundException]{
          os.read(os.resource(classOf[test.os.Testing])/'test/'os/'folder/"file.txt")
        }
        * - intercept[os.ResourceNotFoundException]{
          os.read(os.resource(getClass)/'test/'os/'folder/"file.txt")
        }
        * - intercept[os.ResourceNotFoundException]{
          os.read(os.resource(getClass.getClassLoader)/'folder/"file.txt")
        }
      }
    }
    'rm{
      // shouldn't crash
      os.remove(os.pwd/'out/'scratch/'nonexistent)
    }
    'Mutating{
      val test = os.pwd/'out/'scratch/'test
      os.remove(test)
      os.makedirs(test)
      'cp{
        val d = test/'copying
        'basic{
          assert(
            !os.exists(d/'folder),
            !os.exists(d/'file)
          )
          os.makedirs(d/'folder)
          os.write(d/'file, "omg")
          assert(
            os.exists(d/'folder),
            os.exists(d/'file),
            os.read(d/'file) == "omg"
          )
          os.copy(d/'folder, d/'folder2)
          os.copy(d/'file, d/'file2)

          assert(
            os.exists(d/'folder),
            os.exists(d/'file),
            os.read(d/'file) == "omg",
            os.exists(d/'folder2),
            os.exists(d/'file2),
            os.read(d/'file2) == "omg"
          )
        }
        'deep{
          os.write(d/'folderA/'folderB/'file, "Cow")
          os.copy(d/'folderA, d/'folderC)
          assert(os.read(d/'folderC/'folderB/'file) == "Cow")
        }
      }
      'mv{
        'basic{
          val d = test/'moving
          os.makedirs(d/'folder)
          assert(os.list(d) == Seq(d/'folder))
          os.move(d/'folder, d/'folder2)
          assert(os.list(d) == Seq(d/'folder2))
        }
        'shallow{
          val d = test/'moving2
          os.write(d/"A.scala", "AScala")
          os.write(d/"B.scala", "BScala")
          os.write(d/"A.py", "APy")
          os.write(d/"B.py", "BPy")
          def fileSet = os.list(d).map(_.last).toSet
          assert(fileSet == Set("A.scala", "B.scala", "A.py", "B.py"))
          'partialMoves{
            os.list(d).map(os.move{case r"$x.scala" => s"$x.java"})
            assert(fileSet == Set("A.java", "B.java", "A.py", "B.py"))
            os.list(d).map(os.move{case r"A.$x" => s"C.$x"})
            assert(fileSet == Set("C.java", "B.java", "C.py", "B.py"))
          }
          'fullMoves{
            os.list(d).map(os.move.all{case r"$x.$y" => s"$y.$x"})
            assert(fileSet == Set("scala.A", "scala.B", "py.A", "py.B"))
            def die = os.list(d).map(os.move.all{case r"A.$x" => s"C.$x"})
            intercept[MatchError]{ die }
          }
        }
        'deep{
          val d = test/'moving2
          os.write(d/'scala/'A, "AScala")
          os.write(d/'scala/'B, "BScala")
          os.write(d/'py/'A, "APy")
          os.write(d/'py/'B, "BPy")
          'partialMoves{
            os.list.rec(d).map(os.move*{case d/"py"/x => d/x })
            assert(
              os.list.rec(d).toSet == Set(
                d/'py,
                d/'scala,
                d/'scala/'A,
                d/'scala/'B,
                d/'A,
                d/'B
              )
            )
          }
          'fullMoves{
            def die = os.list.rec(d).map(os.move.all*{case d/"py"/x => d/x })
            intercept[MatchError]{ die }

            os.list.rec(d).filter(_.isFile).map(os.move.all*{
              case d/"py"/x => d/'scala/'py/x
              case d/"scala"/x => d/'py/'scala/x
              case d => println("NOT FOUND " + d); d
            })

            assert(
              os.list.rec(d).toSet == Set(
                d/'py,
                d/'scala,
                d/'py/'scala,
                d/'scala/'py,
                d/'scala/'py/'A,
                d/'scala/'py/'B,
                d/'py/'scala/'A,
                d/'py/'scala/'B
              )
            )
          }
        }
        //          ls! wd | mv*
      }
      'mkdirRm{
        'singleFolder{
          val single = test/'single
          os.makedirs(single/'inner)
          assert(os.list(single) == Seq(single/'inner))
          os.remove(single/'inner)
          assert(os.list(single) == Seq())
        }
        'nestedFolders{
          val nested = test/'nested
          os.makedirs(nested/'inner/'innerer/'innerest)
          assert(
            os.list(nested) == Seq(nested/'inner),
            os.list(nested/'inner) == Seq(nested/'inner/'innerer),
            os.list(nested/'inner/'innerer) == Seq(nested/'inner/'innerer/'innerest)
          )
          os.remove(nested/'inner)
          assert(os.list(nested) == Seq())
        }
      }
      'readWrite{
        val d = test/'readWrite
        os.makedirs(d)
        'simple{
          os.write(d/'file, "i am a cow")
          assert(os.read(d/'file) == "i am a cow")
        }
        'autoMkdir{
          os.write(d/'folder/'folder/'file, "i am a cow")
          assert(os.read(d/'folder/'folder/'file) == "i am a cow")
        }
        'binary{
          os.write(d/'file, Array[Byte](1, 2, 3, 4))
          assert(os.read(d/'file).toSeq == Array[Byte](1, 2, 3, 4).toSeq)
        }
        'concatenating{
          os.write(d/'concat1, Seq("a", "b", "c"))
          assert(os.read(d/'concat1) == "abc")
          os.write(d/'concat2, Array(Array[Byte](1, 2), Array[Byte](3, 4)))
          assert(os.read.bytes(d/'concat2).toSeq == Array[Byte](1, 2, 3, 4).toSeq)
        }
        'writeAppend{
          os.write.append(d/"append.txt", "Hello")
          assert(os.read(d/"append.txt") == "Hello")
          os.write.append(d/"append.txt", " World")
          assert(os.read(d/"append.txt") == "Hello World")
        }
        'writeOver{
          os.write.over(d/"append.txt", "Hello")
          assert(os.read(d/"append.txt") == "Hello")
          os.write.over(d/"append.txt", " Wor")
          assert(os.read(d/"append.txt") == " Wor")
        }
      }
      'Failures{
        val d = test/'failures
        os.makedirs(d)
        'nonexistant{
          * - intercept[nio.NoSuchFileException](os.list(d/'nonexistent))
          * - intercept[nio.NoSuchFileException](os.read(d/'nonexistent))
          * - intercept[os.ResourceNotFoundException](os.read(os.resource/'failures/'nonexistent))
          * - intercept[nio.NoSuchFileException](os.copy(d/'nonexistent, d/'yolo))
          * - intercept[nio.NoSuchFileException](os.move(d/'nonexistent, d/'yolo))
        }
        'collisions{
          os.makedirs(d/'folder)
          os.write(d/'file, "lolol")
          * - intercept[nio.FileAlreadyExistsException](os.move(d/'file, d/'folder))
          * - intercept[nio.FileAlreadyExistsException](os.copy(d/'file, d/'folder))
          * - intercept[nio.FileAlreadyExistsException](os.write(d/'file, "lols"))
         }
      }
    }
  }
}
