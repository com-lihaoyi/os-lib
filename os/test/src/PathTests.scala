package test.os

import java.nio.file.Paths
import java.io.File
import os._
import os.Path.driveRoot
import utest.{assert => _, _}

import java.net.URI
object PathTests extends TestSuite {
  private def nonCanonicalLiteral(providedLiteral: String, sanitizedLiteral: String) =
    s"Literal path sequence [$providedLiteral] used in OS-Lib must be in a canonical form, please use [$sanitizedLiteral] instead"
  private def removeLiteralErr(literal: String) =
    s"Literal path sequence [$literal] doesn't affect path being formed, please remove it"

  val tests = Tests {
    test("Literals") {
      test("implicitConstructors") {
        test("valid") {
          val p: os.Path = "/hello/world"
          val s: os.SubPath = "hello/world"
          val r: os.RelPath = "../hello/world"
          assert(p == os.Path("/hello/world"))
          assert(s == os.SubPath("hello/world"))
          assert(r == os.RelPath("../hello/world"))
        }
        test("invalidLiteral") {
          val err1 = compileError("""val p: os.Path = "hello/world" """)
          assert(err1.msg.contains("Invalid absolute path literal: \"hello/world\""))

          val err2 = compileError("""val s: os.SubPath = "../hello/world" """)
          assert(err2.msg.contains("Invalid subpath literal: \"../hello/world\""))

          val err3 = compileError("""val s: os.SubPath = "/hello/world" """)
          assert(err3.msg.contains("Invalid subpath literal: \"/hello/world\""))

          val err4 = compileError("""val r: os.RelPath = "/hello/world" """)
          assert(err4.msg.contains("Invalid relative path literal: \"/hello/world\""))
        }
        test("nonLiteral") {
          val err1 = compileError("""val str = "hello/world"; val p: os.Path = str """)
          assert(err1.msg.contains("Invalid absolute path literal: str"))

          val err2 = compileError("""val str = "/hello/world"; val s: os.SubPath = str """)
          assert(err2.msg.contains("Invalid subpath literal: str"))

          val err3 = compileError("""val str = "/hello/world"; val r: os.RelPath = str""")
          assert(err3.msg.contains("Invalid relative path literal: str"))
        }
      }
      test("Basic") {
        assert(rel / "src" / "Main/.scala" == rel / "src" / "Main" / ".scala")
        assert(root / "core/src/test" == root / "core" / "src" / "test")
        assert(root / "core/src/test" == root / "core" / "src/test")
      }
      test("literals with [..]") {

        assert(rel / "src" / ".." == rel / "src" / os.up)
        assert(root / "src" / ".." == root / "src" / os.up)
        assert(root / "hello" / ".." / "world" == root / "hello" / os.up / "world")
        assert(root / "hello" / "../world" == root / "hello" / os.up / "world")
      }

      test("Compile errors") {

        compileError("""root / "src/../foo"""").check("", nonCanonicalLiteral("src/../foo", "foo"))
        compileError("""root / "hello/../world"""").check(
          "",
          nonCanonicalLiteral("hello/../world", "world")
        )
        compileError("""root / "src/../foo/bar"""").check(
          "",
          nonCanonicalLiteral("src/../foo/bar", "foo/bar")
        )
        compileError("""root / "src/../foo/bar/.."""").check(
          "",
          nonCanonicalLiteral("src/../foo/bar/..", "foo")
        )
        compileError("""root / "src/../foo/../bar/."""").check(
          "",
          nonCanonicalLiteral("src/../foo/../bar/.", "bar")
        )
        compileError("""root / "src/foo/./.."""").check(
          "",
          nonCanonicalLiteral("src/foo/./..", "src")
        )
        compileError("""root / "src/foo//./.."""").check(
          "",
          nonCanonicalLiteral("src/foo//./..", "src")
        )

        compileError("""root / "src/.."""").check("", removeLiteralErr("src/.."))
        compileError("""root / "src/../foo/.."""").check("", removeLiteralErr("src/../foo/.."))
        compileError("""root / "src/foo/../.."""").check("", removeLiteralErr("src/foo/../.."))
        compileError("""root / "src/foo/./../.."""").check("", removeLiteralErr("src/foo/./../.."))
        compileError("""root / "src/./foo/./../.."""").check(
          "",
          removeLiteralErr("src/./foo/./../..")
        )
        compileError("""root / "src///foo/./../.."""").check(
          "",
          removeLiteralErr("src///foo/./../..")
        )

        compileError("""root / "/" """).check("", removeLiteralErr("/"))
        compileError("""root / "/ " """).check("", nonCanonicalLiteral("/ ", " "))
        compileError("""root / " /" """).check("", nonCanonicalLiteral(" /", " "))
        compileError("""root / "//" """).check("", removeLiteralErr("//"))

        compileError("""root / "foo/" """).check("", nonCanonicalLiteral("foo/", "foo"))
        compileError("""root / "foo//" """).check("", nonCanonicalLiteral("foo//", "foo"))

        compileError("""root / "foo/bar/" """).check("", nonCanonicalLiteral("foo/bar/", "foo/bar"))
        compileError("""root / "foo/bar//" """).check(
          "",
          nonCanonicalLiteral("foo/bar//", "foo/bar")
        )

        compileError("""root / "/foo" """).check("", nonCanonicalLiteral("/foo", "foo"))
        compileError("""root / "//foo" """).check("", nonCanonicalLiteral("//foo", "foo"))

        compileError("""root / "//foo/" """).check("", nonCanonicalLiteral("//foo/", "foo"))

        compileError(""" rel / "src" / "" """).check("", removeLiteralErr(""))
        compileError(""" rel / "src" / "." """).check("", removeLiteralErr("."))

        compileError(""" root / "src/"  """).check("", nonCanonicalLiteral("src/", "src"))
        compileError(""" root / "src/." """).check("", nonCanonicalLiteral("src/.", "src"))

        compileError(""" root / "" """).check("", removeLiteralErr(""))
        compileError(""" root / "." """).check("", removeLiteralErr("."))

      }
    }
    test("Basic") {
      val base = rel / "src" / "main" / "scala"
      val subBase = sub / "src" / "main" / "scala"

      test("Transform posix paths") {
        // verify posix string format of driveRelative path
        assert(posix(root / "omg") == posix(Paths.get("/omg").toAbsolutePath))

        // verify driveRelative path
        assert(sameFile((root / "omg").wrapped, Paths.get("/omg")))

        // driveRelative path is an absolute path
        assert(posix(root / "omg") == s"$driveRoot/omg")

        // Paths.get(driveRoot) same file as pwd
        val p1 = posix(Paths.get(driveRoot).toAbsolutePath) match {
          case s if s.matches(".:.*/") =>
            s.stripSuffix("/") // java 8, remove spurious trailing slash
          case s =>
            s
        }
        val p2 = posix(pwd.toNIO.toAbsolutePath)
        System.err.printf("p1[%s]\np2[%s]\n", p1, p2)
        assert(p1 == p2)
      }
      test("Transformers") {
        // java.nio.file.Path to os.Path
        assert(root / "omg" == Path(Paths.get("/omg")))
        assert(rel / "omg" == RelPath(Paths.get("omg")))
        assert(sub / "omg" == SubPath(Paths.get("omg")))

        // URI to os.Path
        assert(root / "omg" == Path(Paths.get("/omg").toUri()))

        // We only support file schemes like above, but nothing else
        val httpUri = URI.create(
          "https://john.doe@www.example.com:123/forum/questions/?tag=networking&order=newest#top"
        )
        val ldapUri = URI.create(
          "ldap://[2001:db8::7]/c=GB?objectClass?one"
        )
        intercept[IllegalArgumentException](Path(httpUri))
        intercept[IllegalArgumentException](Path(ldapUri))

        // os.Path to String
        assert((rel / "omg").toString == "omg")
        assert((sub / "omg").toString == "omg")
        assert((up / "omg").toString == "../omg")
        assert((up / up / "omg").toString == "../../omg")

        // String to os.Path
        assert(root / "omg" == Path("/omg"))
        assert(rel / "omg" == RelPath("omg"))
        assert(sub / "omg" == SubPath("omg"))
      }

      test("BasePath") {
        test("baseName") {
          assert((base / "baseName.ext").baseName == "baseName")
          assert((base / "baseName.v2.0.ext").baseName == "baseName.v2.0")
          assert((base / "baseOnly").baseName == "baseOnly")
          assert((base / "baseOnly.").baseName == "baseOnly")
        }

        test("ext") {
          assert((base / "baseName.ext").ext == "ext")
          assert((base / "baseName.v2.0.ext").ext == "ext")
          assert((base / "baseOnly").ext == "")
          assert((base / "baseOnly.").ext == "")
        }

        test("emptyExt") {
          os.root.ext ==> ""
          os.rel.ext ==> ""
          os.sub.ext ==> ""
          os.up.ext ==> ""
        }

        test("emptyLast") {
          intercept[PathError.LastOnEmptyPath](os.root.last).getMessage ==>
            "empty path has no last segment"
          intercept[PathError.LastOnEmptyPath](os.rel.last).getMessage ==>
            "empty path has no last segment"
          intercept[PathError.LastOnEmptyPath](os.sub.last).getMessage ==>
            "empty path has no last segment"
          intercept[PathError.LastOnEmptyPath](os.up.last).getMessage ==>
            "empty path has no last segment"
        }
      }

      test("RelPath") {
        test("Constructors") {
          test("Symbol") {
            val rel1 = base / "ammonite"
            assert(
              rel1.segments == Seq("src", "main", "scala", "ammonite"),
              rel1.toString == "src/main/scala/ammonite"
            )
          }
          test("String") {
            val rel1 = base / "Path.scala"
            assert(
              rel1.segments == Seq("src", "main", "scala", "Path.scala"),
              rel1.toString == "src/main/scala/Path.scala"
            )
          }
          test("Combos") {
            def check(rel1: RelPath) = assert(
              rel1.segments == Seq("src", "main", "scala", "sub1", "sub2"),
              rel1.toString == "src/main/scala/sub1/sub2"
            )
            test("ArrayString") {
              val arr = Array("sub1", "sub2")
              check(base / arr)
            }
            test("ArraySymbol") {
              val arr = Array("sub1", "sub2")
              check(base / arr)
            }
            test("SeqString") {
              check(base / Seq("sub1", "sub2"))
            }
            test("SeqSymbol") {
              check(base / Seq("sub1", "sub2"))
            }
            test("SeqSeqSeqSymbol") {
              check(
                base / Seq(Seq(Seq("sub1"), Seq()), Seq(Seq("sub2")), Seq())
              )
            }
          }
        }
        test("Relativize") {
          def eq[T](p: T, q: T) = assert(p == q)
          test - eq(rel / "omg" / "bbq" / "wtf" relativeTo rel / "omg" / "bbq" / "wtf", rel)
          test - eq(rel / "omg" / "bbq" relativeTo rel / "omg" / "bbq" / "wtf", up)
          test - eq(rel / "omg" / "bbq" / "wtf" relativeTo rel / "omg" / "bbq", rel / "wtf")
          test - eq(rel / "omg" / "bbq" relativeTo rel / "omg" / "bbq" / "wtf", up)
          test - eq(up / "omg" / "bbq" relativeTo rel / "omg" / "bbq", up / up / up / "omg" / "bbq")
          test - intercept[PathError.NoRelativePath](
            rel / "omg" / "bbq" relativeTo up / "omg" / "bbq"
          )
        }
      }
      test("SubPath") {
        test("Constructors") {
          test("Symbol") {
            val rel1 = subBase / "ammonite"
            assert(
              rel1.segments == Seq("src", "main", "scala", "ammonite"),
              rel1.toString == "src/main/scala/ammonite"
            )
          }
          test("String") {
            val rel1 = subBase / "Path.scala"
            assert(
              rel1.segments == Seq("src", "main", "scala", "Path.scala"),
              rel1.toString == "src/main/scala/Path.scala"
            )
          }
          test("Combos") {
            def check(rel1: SubPath) = assert(
              rel1.segments == Seq("src", "main", "scala", "sub1", "sub2"),
              rel1.toString == "src/main/scala/sub1/sub2"
            )
            test("ArrayString") {
              val arr = Array("sub1", "sub2")
              check(subBase / arr)
            }
            test("ArraySymbol") {
              val arr = Array("sub1", "sub2")
              check(subBase / arr)
            }
            test("SeqString") {
              check(subBase / Seq("sub1", "sub2"))
            }
            test("SeqSymbol") {
              check(subBase / Seq("sub1", "sub2"))
            }
            test("SeqSeqSeqSymbol") {
              check(
                subBase / Seq(Seq(Seq("sub1"), Seq()), Seq(Seq("sub2")), Seq())
              )
            }
          }
        }
        test("Relativize") {
          def eq[T](p: T, q: T) = assert(p == q)
          test - eq(sub / "omg" / "bbq" / "wtf" relativeTo sub / "omg" / "bbq" / "wtf", rel)
          test - eq(sub / "omg" / "bbq" relativeTo sub / "omg" / "bbq" / "wtf", up)
          test - eq(sub / "omg" / "bbq" / "wtf" relativeTo sub / "omg" / "bbq", rel / "wtf")
          test - eq(sub / "omg" / "bbq" relativeTo sub / "omg" / "bbq" / "wtf", up)
        }
      }

      test("AbsPath") {
        val d = pwd
        val abs = d / base
        test("Constructor") {
          assert(
            posix(abs).drop(d.toString.length) == "/src/main/scala",
            abs.toString.length > d.toString.length
          )
        }
        test("Relativize") {
          def eq[T](p: T, q: T) = assert(p == q)
          test - eq(root / "omg" / "bbq" / "wtf" relativeTo root / "omg" / "bbq" / "wtf", rel)
          test - eq(root / "omg" / "bbq" relativeTo root / "omg" / "bbq" / "wtf", up)
          test - eq(root / "omg" / "bbq" / "wtf" relativeTo root / "omg" / "bbq", rel / "wtf")
          test - eq(root / "omg" / "bbq" relativeTo root / "omg" / "bbq" / "wtf", up)
          test - intercept[PathError.NoRelativePath](
            rel / "omg" / "bbq" relativeTo up / "omg" / "bbq"
          )
        }
      }
      test("Ups") {
        test("RelativeUps") {
          val rel2 = base / up
          assert(rel2 == rel / "src" / "main")
          assert(base / up / up == rel / "src")
          assert(base / up / up / up == rel)
          assert(base / up / up / up / up == up)
          assert(base / up / up / up / up / up == up / up)
          assert(up / base == up / "src" / "main" / "scala")
        }
        test("AbsoluteUps") {
          // Keep applying `up` and verify that the path gets
          // shorter and shorter and eventually errors.
          var abs = pwd
          var i = abs.segmentCount
          while (i > 0) {
            abs /= up
            i -= 1
            assert(abs.segmentCount == i)
          }
          intercept[PathError.AbsolutePathOutsideRoot.type] { abs / up }
        }
        test("RootUpBreak") {
          intercept[PathError.AbsolutePathOutsideRoot.type] { root / up }
          val x = root / "omg"
          val y = x / up
          intercept[PathError.AbsolutePathOutsideRoot.type] { y / up }
        }
      }
      test("Comparison") {
        test("Relative") - {
          assert(rel / "omg" / "wtf" == rel / "omg" / "wtf")
          assert(rel / "omg" / "wtf" != rel / "omg" / "wtf" / "bbq")
          assert(rel / "omg" / "wtf" / "bbq" startsWith rel / "omg" / "wtf")
          assert(rel / "omg" / "wtf" startsWith rel / "omg" / "wtf")
          assert(up / "omg" / "wtf" startsWith up / "omg" / "wtf")
          assert(!(rel / "omg" / "wtf" startsWith rel / "omg" / "wtf" / "bbq"))
          assert(!(up / "omg" / "wtf" startsWith rel / "omg" / "wtf"))
          assert(!(rel / "omg" / "wtf" startsWith up / "omg" / "wtf"))
        }
        test("Absolute") - {
          assert(root / "omg" / "wtf" == root / "omg" / "wtf")
          assert(root / "omg" / "wtf" != root / "omg" / "wtf" / "bbq")
          assert(root / "omg" / "wtf" / "bbq" startsWith root / "omg" / "wtf")
          assert(root / "omg" / "wtf" startsWith root / "omg" / "wtf")
          assert(!(root / "omg" / "wtf" startsWith root / "omg" / "wtf" / "bbq"))
        }
        test("Invalid") {
          compileError("""root/"omg"/"wtf" < "omg"/"wtf"""")
          compileError("""root/"omg"/"wtf" > "omg"/"wtf"""")
          compileError(""""omg"/"wtf" < root/"omg"/"wtf"""")
          compileError(""""omg"/"wtf" > root/"omg"/"wtf"""")
        }
      }
    }
    test("Errors") {
      def nonLiteral(s: String) = s

      test("InvalidChars") {
        val ex = intercept[PathError.InvalidSegment](rel / "src" / nonLiteral("Main/.scala"))

        val PathError.InvalidSegment("Main/.scala", msg1) = ex

        assert(
          msg1.contains(
            "[/] is not a valid character to appear in a non-literal path segment. If you are " +
              "dealing with dynamic path-strings coming from external sources, use the " +
              "Path(...)/RelPath(...)/SubPath(...) constructor calls to convert them."
          )
        )

        val ex2 = intercept[PathError.InvalidSegment](root / "hello" / nonLiteral("..") / "world")

        val PathError.InvalidSegment("..", msg2) = ex2

        assert(msg2.contains("use the `up` segment from `os.up`"))
      }
      test("InvalidSegments") {
        intercept[PathError.InvalidSegment] { root / nonLiteral("core/src/test") }
        intercept[PathError.InvalidSegment] { root / nonLiteral("") }
        intercept[PathError.InvalidSegment] { root / nonLiteral(".") }
        intercept[PathError.InvalidSegment] { root / nonLiteral("..") }
      }
      test("EmptySegment") {
        intercept[PathError.InvalidSegment](rel / "src" / nonLiteral(""))
        intercept[PathError.InvalidSegment](rel / "src" / nonLiteral("."))
        intercept[PathError.InvalidSegment](rel / "src" / nonLiteral(".."))
      }
      test("CannotRelativizeAbsAndRel") {
        val abs = pwd
        val rel = os.rel / "omg" / "wtf"
        compileError("""
        abs.relativeTo(rel)
      """).msg.toLowerCase.contains("required: os.path") ==> true
        compileError("""
        rel.relativeTo(abs)
      """).msg.toLowerCase.contains("required: os.relpath") ==> true
      }
      test("InvalidCasts") {
        intercept[IllegalArgumentException](Path("omg/cow"))
        intercept[IllegalArgumentException](RelPath("/omg/cow"))
      }
      test("Pollution") {
        // Make sure we"re" not polluting too much
        compileError(""""omg".ext""")
        compileError(""" "omg".ext """)
      }
    }
    test("Extractors") {
      test("paths") {
        val a / b / c / d / "omg" = pwd / "A" / "B" / "C" / "D" / "omg"
        assert(a == pwd / "A")
        assert(b == "B")
        assert(c == "C")
        assert(d == "D")

        // If the paths aren"t" deep enough, it
        // just doesn"t" match but doesn"t" blow up
        root / "omg" match {
          case a3 / b3 / c3 / d3 / e3 => assert(false)
          case _ =>
        }
      }
    }
    test("sorting") {
      test - {
        assert(
          Seq(root / "c", root, root / "b", root / "a").sorted ==
            Seq(root, root / "a", root / "b", root / "c")
        )
      }

      test - assert(
        Seq(up / "c", up / up / "c", rel / "b" / "c", rel / "a" / "c", rel / "a" / "d").sorted ==
          Seq(rel / "a" / "c", rel / "a" / "d", rel / "b" / "c", up / "c", up / up / "c")
      )

      test - assert(
        Seq(os.root / "yo", os.root / "yo").sorted ==
          Seq(os.root / "yo", os.root / "yo")
      )
    }
    test("construction") {
      test("success") {
        val relStr = "hello/cow/world/.."
        val absStr = "/hello/world"

        val lhs = Path(absStr)
        val rhs = root / "hello" / "world"
        assert(
          RelPath(relStr) == rel / "hello" / "cow",
          // Path(...) also allows paths starting with ~,
          // which is expanded to become your home directory
          lhs == rhs
        )

        // You can also pass in java.io.File and java.nio.file.Path
        // objects instead of Strings when constructing paths
        val relIoFile = new java.io.File(relStr)
        val absNioFile = java.nio.file.Paths.get(absStr)

        assert(RelPath(relIoFile) == rel / "hello" / "cow")
        assert(Path(absNioFile) == root / "hello" / "world")
        assert(Path(relIoFile, root / "base") == root / "base" / "hello" / "cow")
      }
      test("basepath") {
        val relStr = "hello/cow/world/.."
        val absStr = "/hello/world"
        assert(
          FilePath(relStr) == rel / "hello" / "cow",
          FilePath(absStr) == root / "hello" / "world"
        )
      }
      test("based") {
        val relStr = "hello/cow/world/.."
        val absStr = "/hello/world"
        val basePath: FilePath = FilePath(relStr)
        assert(Path(relStr, root / "base") == root / "base" / "hello" / "cow")
        assert(Path(absStr, root / "base") == root / "hello" / "world")
        assert(Path(basePath, root / "base") == root / "base" / "hello" / "cow")
        assert(Path(".", pwd).last != "")
      }
      test("failure") {
        val relStr = "hello/.."
        intercept[java.lang.IllegalArgumentException] {
          Path(relStr)
        }

        val absStr = "/hello"
        intercept[java.lang.IllegalArgumentException] {
          RelPath(absStr)
        }

        val tooManyUpsStr = "/hello/../.."
        intercept[PathError.AbsolutePathOutsideRoot.type] {
          Path(tooManyUpsStr)
        }
      }
    }
    test("issue159") {
      val result1 = os.rel / Seq(os.up, os.rel / "hello", os.rel / "world")
      val result2 = os.rel / Array(os.up, os.rel / "hello", os.rel / "world")
      val expected = os.up / "hello" / "world"
      assert(result1 == expected)
      assert(result2 == expected)
    }
    test("custom root") {
      assert(os.root == os.root(os.root.root))
      File.listRoots().foreach { root =>
        val path = os.root(root.toPath().toString) / "test" / "dir"
        assert(path.root == root.toString)
        assert(path.relativeTo(os.root(root.toPath().toString)) == rel / "test" / "dir")
      }
    }
    test("issue201") {
      val p = Path("/omg") // driveRelative path does not throw exception.
      System.err.printf("p[%s]\n", posix(p))
      assert(posix(p) contains "/omg")
    }
    test("dynamicPwd") {
      val x = os.pwd
      val y = os.dynamicPwd.withValue(os.pwd / "hello") {
        os.pwd
      }

      assert(x / "hello" == y)
    }
    test("pathRelativizerSerializer") {
      val base = os.pwd / "base"
      val serializer = os.Path.pathRelativizerSerializer(base)
      os.Path.pathSerializer.withValue(serializer) {
        val inBase = base / "foo" / "bar"
        val outside = os.pwd / "outside"

        assert(inBase.toString == "foo/bar")
        assert(inBase.toNIO == java.nio.file.Paths.get("foo/bar"))
        assert(inBase.toIO.getPath == new java.io.File("foo/bar").getPath)

        assert(os.Path("foo/bar") == inBase)
        assert(os.Path(java.nio.file.Paths.get("foo/bar")) == inBase)
        assert(os.Path(new java.io.File("foo/bar")) == inBase)

        assert(outside.toString == outside.wrapped.toString)
        assert(outside.toNIO == outside.wrapped)
      }
    }
    test("pathRemapSerializer") {
      val from = os.pwd / "from"
      val to = os.pwd / "to"
      val serializer = os.Path.pathRemapSerializer(from, to)
      os.Path.pathSerializer.withValue(serializer) {
        val inFrom = from / "foo" / "bar"
        val inTo = to / "foo" / "bar"
        val outside = os.pwd / "outside"

        assert(inFrom.toString == (to / "foo" / "bar").wrapped.toString)
        assert(inFrom.toNIO == (to / "foo" / "bar").wrapped)
        assert(inFrom.toIO.getPath == (to / "foo" / "bar").wrapped.toString)

        assert(os.Path((to / "foo" / "bar").wrapped.toString) == inFrom)
        assert(os.Path((to / "foo" / "bar").wrapped) == inFrom)
        assert(os.Path(new java.io.File((to / "foo" / "bar").wrapped.toString)) == inFrom)

        assert(inTo.toString == inTo.wrapped.toString)
        assert(os.Path(inTo.wrapped.toString) == inFrom)

        assert(outside.toString == outside.wrapped.toString)
        assert(outside.toNIO == outside.wrapped)
      }
    }
    test("pathRemapSerializerMultipleMappings") {
      val from1 = os.pwd / "from1"
      val to1 = os.pwd / "to1"
      val from2 = os.pwd / "from2"
      val to2 = os.pwd / "to2"
      val serializer = os.Path.pathRemapSerializer(Seq((from1, to1), (from2, to2)))
      os.Path.pathSerializer.withValue(serializer) {
        val inFrom1 = from1 / "a"
        val inFrom2 = from2 / "b"
        val inTo1 = to1 / "a"
        val inTo2 = to2 / "b"

        assert(inFrom1.toString == inTo1.wrapped.toString)
        assert(inFrom2.toString == inTo2.wrapped.toString)
        assert(os.Path(inTo1.wrapped.toString) == inFrom1)
        assert(os.Path(inTo2.wrapped.toString) == inFrom2)

        // First matching mapping is applied, without chaining remaps.
        val chainSrc = from1 / "x"
        val chainSerializer =
          os.Path.pathRemapSerializer(Seq((from1, to1), (to1, to2)))
        os.Path.pathSerializer.withValue(chainSerializer) {
          assert(chainSrc.toString == to1.wrapped.resolve("x").toString)
        }
      }
    }
  }
  // compare absolute paths
  def sameFile(a: java.nio.file.Path, b: java.nio.file.Path): Boolean = {
    a.toAbsolutePath == b.toAbsolutePath
  }
  def sameFile(a: os.Path, b: java.nio.file.Path): Boolean = {
    sameFile(a.wrapped, b)
  }
  def sameFile(a: Path, b: Path): Boolean = {
    sameFile(a.wrapped, b.wrapped)
  }
  def posix(s: String): String = s.replace('\\', '/')
  def posix(p: java.nio.file.Path): String = posix(p.toString)
  def posix(p: os.Path): String = posix(p.toNIO)
}
