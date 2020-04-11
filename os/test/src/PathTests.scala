package test.os

import java.nio.file.Paths

import os._
import utest._
object PathTests extends TestSuite{
  val tests = Tests {
    test("Basic"){
      val base = rel/'src/'main/'scala
      val subBase = sub/'src/'main/'scala
      test("Transformers"){
        if(Unix()){
          assert(
            // ammonite.Path to java.nio.file.Path
            (root/'omg).wrapped == Paths.get("/omg"),

            // java.nio.file.Path to ammonite.Path
            root/'omg == Path(Paths.get("/omg")),
            rel/'omg == RelPath(Paths.get("omg")),
            sub/'omg == SubPath(Paths.get("omg")),

            // ammonite.Path to String
            (root/'omg).toString == "/omg",
            (rel/'omg).toString == "omg",
            (sub/'omg).toString == "omg",
            (up/'omg).toString == "../omg",
            (up/up/'omg).toString == "../../omg",

            // String to ammonite.Path
            root/'omg == Path("/omg"),
            rel/'omg == RelPath("omg"),
            sub/'omg == SubPath("omg")
          )
        }
      }

      test("BasePath"){
        test("baseName"){
          assert((base / "baseName.ext").baseName == "baseName")
          assert((base / "baseName.v2.0.ext").baseName == "baseName.v2.0")
          assert((base / "baseOnly").baseName == "baseOnly")
          assert((base / "baseOnly.").baseName == "baseOnly")
        }

        test("ext"){
          assert((base / "baseName.ext").ext == "ext")
          assert((base / "baseName.v2.0.ext").ext == "ext")
          assert((base / "baseOnly").ext == "")
          assert((base / "baseOnly.").ext == "")
        }
      }

      test("RelPath"){
        test("Constructors"){
          test("Symbol"){
            if (Unix()){
              val rel1 = base / 'ammonite
              assert(
                rel1.segments == Seq("src", "main", "scala", "ammonite"),
                rel1.toString == "src/main/scala/ammonite"
              )
            }
          }
          test("String"){
            if (Unix()){
              val rel1 = base / "Path.scala"
              assert(
                rel1.segments == Seq("src", "main", "scala", "Path.scala"),
                rel1.toString == "src/main/scala/Path.scala"
              )
            }
          }
          test("Combos"){
            def check(rel1: RelPath) = assert(
              rel1.segments == Seq("src", "main", "scala", "sub1", "sub2"),
              rel1.toString == "src/main/scala/sub1/sub2"
            )
            test("ArrayString"){
              if (Unix()){
                val arr = Array("sub1", "sub2")
                check(base / arr)
              }
            }
            test("ArraySymbol"){
              if (Unix()){
                val arr = Array('sub1, 'sub2)
                check(base / arr)
              }
            }
            test("SeqString"){
              if (Unix()) check(base / Seq("sub1", "sub2"))
            }
            test("SeqSymbol"){
              if (Unix()) check(base / Seq('sub1, 'sub2))
            }
            test("SeqSeqSeqSymbol"){
              if (Unix()){
                check(
                  base / Seq(Seq(Seq('sub1), Seq()), Seq(Seq('sub2)), Seq())
                )
              }
            }
          }
        }
        test("Relativize"){
          def eq[T](p: T, q: T) = assert(p == q)
          test - eq(rel/'omg/'bbq/'wtf relativeTo rel/'omg/'bbq/'wtf, rel)
          test - eq(rel/'omg/'bbq relativeTo rel/'omg/'bbq/'wtf, up)
          test - eq(rel/'omg/'bbq/'wtf relativeTo rel/'omg/'bbq, rel/'wtf)
          test - eq(rel/'omg/'bbq relativeTo rel/'omg/'bbq/'wtf, up)
          test - eq(up/'omg/'bbq relativeTo rel/'omg/'bbq, up/up/up/'omg/'bbq)
          test - intercept[PathError.NoRelativePath](rel/'omg/'bbq relativeTo up/'omg/'bbq)
        }
      }
      test("SubPath"){
        test("Constructors"){
          test("Symbol"){
            if (Unix()){
              val rel1 = subBase / 'ammonite
              assert(
                rel1.segments == Seq("src", "main", "scala", "ammonite"),
                rel1.toString == "src/main/scala/ammonite"
              )
            }
          }
          test("String"){
            if (Unix()){
              val rel1 = subBase / "Path.scala"
              assert(
                rel1.segments == Seq("src", "main", "scala", "Path.scala"),
                rel1.toString == "src/main/scala/Path.scala"
              )
            }
          }
          test("Combos"){
            def check(rel1: SubPath) = assert(
              rel1.segments == Seq("src", "main", "scala", "sub1", "sub2"),
              rel1.toString == "src/main/scala/sub1/sub2"
            )
            test("ArrayString"){
              if (Unix()){
                val arr = Array("sub1", "sub2")
                check(subBase / arr)
              }
            }
            test("ArraySymbol"){
              if (Unix()){
                val arr = Array('sub1, 'sub2)
                check(subBase / arr)
              }
            }
            test("SeqString"){
              if (Unix()) check(subBase / Seq("sub1", "sub2"))
            }
            test("SeqSymbol"){
              if (Unix()) check(subBase / Seq('sub1, 'sub2))
            }
            test("SeqSeqSeqSymbol"){
              if (Unix()){
                check(
                  subBase / Seq(Seq(Seq('sub1), Seq()), Seq(Seq('sub2)), Seq())
                )
              }
            }
          }
        }
        test("Relativize"){
          def eq[T](p: T, q: T) = assert(p == q)
          test - eq(sub/'omg/'bbq/'wtf relativeTo sub/'omg/'bbq/'wtf, rel)
          test - eq(sub/'omg/'bbq relativeTo sub/'omg/'bbq/'wtf, up)
          test - eq(sub/'omg/'bbq/'wtf relativeTo sub/'omg/'bbq, rel/'wtf)
          test - eq(sub/'omg/'bbq relativeTo sub/'omg/'bbq/'wtf, up)
        }
      }

      test("AbsPath"){
        val d = pwd
        val abs = d / base
        test("Constructor"){
          if (Unix()) assert(
            abs.toString.drop(d.toString.length) == "/src/main/scala",
            abs.toString.length > d.toString.length
          )
        }
        test("Relativize"){
          def eq[T](p: T, q: T) = assert(p == q)
          test - eq(root/'omg/'bbq/'wtf relativeTo root/'omg/'bbq/'wtf, rel)
          test - eq(root/'omg/'bbq relativeTo root/'omg/'bbq/'wtf, up)
          test - eq(root/'omg/'bbq/'wtf relativeTo root/'omg/'bbq, rel/'wtf)
          test - eq(root/'omg/'bbq relativeTo root/'omg/'bbq/'wtf, up)
          test - intercept[PathError.NoRelativePath](rel/'omg/'bbq relativeTo up/'omg/'bbq)
        }
      }
      test("Ups"){
        test("RelativeUps"){
          val rel2 = base/up
          assert(
            rel2 == rel/'src/'main,
            base/up/up == rel/'src,
            base/up/up/up == rel,
            base/up/up/up/up == up,
            base/up/up/up/up/up == up/up,
            up/base == up/'src/'main/'scala
          )
        }
        test("AbsoluteUps"){
          // Keep applying `up` and verify that the path gets
          // shorter and shorter and eventually errors.
          var abs = pwd
          var i = abs.segmentCount
          while(i > 0){
            abs /= up
            i-=1
            assert(abs.segmentCount == i)
          }
          intercept[PathError.AbsolutePathOutsideRoot.type]{ abs/up }
        }
        test("RootUpBreak"){
          intercept[PathError.AbsolutePathOutsideRoot.type]{ root/up }
          val x = root/"omg"
          val y = x/up
          intercept[PathError.AbsolutePathOutsideRoot.type]{ y / up }
        }
      }
      test("Comparison"){
        test("Relative") - assert(
          rel/'omg/'wtf == rel/'omg/'wtf,
          rel/'omg/'wtf != rel/'omg/'wtf/'bbq,
          rel/'omg/'wtf/'bbq startsWith rel/'omg/'wtf,
          rel/'omg/'wtf startsWith rel/'omg/'wtf,
          up/'omg/'wtf startsWith up/'omg/'wtf,
          !(rel/'omg/'wtf startsWith rel/'omg/'wtf/'bbq),
          !(up/'omg/'wtf startsWith rel/'omg/'wtf),
          !(rel/'omg/'wtf startsWith up/'omg/'wtf)
        )
        test("Absolute") - assert(
          root/'omg/'wtf == root/'omg/'wtf,
          root/'omg/'wtf != root/'omg/'wtf/'bbq,
          root/'omg/'wtf/'bbq startsWith root/'omg/'wtf,
          root/'omg/'wtf startsWith root/'omg/'wtf,
          !(root/'omg/'wtf startsWith root/'omg/'wtf/'bbq)
        )
        test("Invalid"){
          compileError("""root/'omg/'wtf < 'omg/'wtf""")
          compileError("""root/'omg/'wtf > 'omg/'wtf""")
          compileError("""'omg/'wtf < root/'omg/'wtf""")
          compileError("""'omg/'wtf > root/'omg/'wtf""")
        }
      }
    }
    test("Errors"){
      test("InvalidChars"){
        val ex = intercept[PathError.InvalidSegment](rel/'src/"Main/.scala")

        val PathError.InvalidSegment("Main/.scala", msg1) = ex

        assert(msg1.contains("[/] is not a valid character to appear in a path segment"))

        val ex2 = intercept[PathError.InvalidSegment](root/"hello"/".."/"world")

        val PathError.InvalidSegment("..", msg2) = ex2

        assert(msg2.contains("use the `up` segment from `os.up`"))
      }
      test("InvalidSegments"){
        intercept[PathError.InvalidSegment]{root/ "core/src/test"}
        intercept[PathError.InvalidSegment]{root/ ""}
        intercept[PathError.InvalidSegment]{root/ "."}
        intercept[PathError.InvalidSegment]{root/ ".."}
      }
      test("EmptySegment"){
        intercept[PathError.InvalidSegment](rel/'src / "")
        intercept[PathError.InvalidSegment](rel/'src / ".")
        intercept[PathError.InvalidSegment](rel/'src / "..")
      }
      test("CannotRelativizeAbsAndRel"){if(Unix()){
        val abs = pwd
        val rel = os.rel/'omg/'wtf
        compileError("""
          abs relativeTo rel
        """).check(
          """
          abs relativeTo rel
                         ^
          """,
          "type mismatch"
        )
        compileError("""
          rel relativeTo abs
                     """).check(
            """
          rel relativeTo abs
                         ^
            """,
            "type mismatch"
          )
      }}
      test("InvalidCasts"){
        if(Unix()){
          intercept[IllegalArgumentException](Path("omg/cow"))
          intercept[IllegalArgumentException](RelPath("/omg/cow"))
        }
      }
      test("Pollution"){
        // Make sure we're not polluting too much
        compileError("""'omg.ext""")
        compileError(""" "omg".ext """)
      }
    }
    test("Extractors"){
      test("paths"){
        val a/b/c/d/"omg" = pwd/'A/'B/'C/'D/"omg"
        assert(
          a == pwd/'A,
          b == "B",
          c == "C",
          d == "D"
        )

        // If the paths aren't deep enough, it
        // just doesn't match but doesn't blow up
        root/'omg match {
          case a3/b3/c3/d3/e3 => assert(false)
          case _ =>
        }
      }
    }
    test("sorting"){
      assert(
        Seq(root/'c, root, root/'b, root/'a).sorted == Seq(root, root/'a, root/'b, root/'c),
        Seq(up/'c, up/up/'c, rel/'b/'c, rel/'a/'c, rel/'a/'d).sorted ==
          Seq(rel/'a/'c, rel/'a/'d, rel/'b/'c, up/'c, up/up/'c)
      )
    }
    test("construction"){
      test("success"){
        if(Unix()){
          val relStr = "hello/cow/world/.."
          val absStr = "/hello/world"

          val lhs = Path(absStr)
          val rhs = root/'hello/'world
          assert(
            RelPath(relStr) == rel/'hello/'cow,
            // Path(...) also allows paths starting with ~,
            // which is expanded to become your home directory
            lhs == rhs
          )

          // You can also pass in java.io.File and java.nio.file.Path
          // objects instead of Strings when constructing paths
          val relIoFile = new java.io.File(relStr)
          val absNioFile = java.nio.file.Paths.get(absStr)

          assert(
            RelPath(relIoFile) == rel/'hello/'cow,
            Path(absNioFile) == root/'hello/'world,
            Path(relIoFile, root/'base) == root/'base/'hello/'cow
          )
        }
      }
      test("basepath"){
        if(Unix()){
          val relStr = "hello/cow/world/.."
          val absStr = "/hello/world"
          assert(
            FilePath(relStr) == rel/'hello/'cow,
            FilePath(absStr) == root/'hello/'world
          )
        }
      }
      test("based"){
        if(Unix()){
          val relStr = "hello/cow/world/.."
          val absStr = "/hello/world"
          val basePath: FilePath = FilePath(relStr)
          assert(
            Path(relStr, root/'base) == root/'base/'hello/'cow,
            Path(absStr, root/'base) == root/'hello/'world,
            Path(basePath, root/'base) == root/'base/'hello/'cow,
            Path(".", pwd).last != ""
          )
        }
      }
      test("failure"){
        if(Unix()){
          val relStr = "hello/.."
          intercept[java.lang.IllegalArgumentException]{
            Path(relStr)
          }

          val absStr = "/hello"
          intercept[java.lang.IllegalArgumentException]{
            RelPath(absStr)
          }

          val tooManyUpsStr = "/hello/../.."
          intercept[PathError.AbsolutePathOutsideRoot.type]{
            Path(tooManyUpsStr)
          }
        }
      }
    }
  }
}
