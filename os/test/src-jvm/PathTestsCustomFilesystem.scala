package test.os

import utest._
import os._

import java.util.HashMap
import java.nio.file.{FileAlreadyExistsException, FileSystem, FileSystems}
import java.net.URI
import scala.util.{Failure, Try}
import scala.util.control.NonFatal

object PathTestsCustomFilesystem extends TestSuite {

  def customFsUri(jarName: String = "foo.jar") = {
    val path = java.nio.file.Paths.get(jarName);
    path.toUri()
  }

  def withCustomFs[T](f: FileSystem => T, fsUri: URI = customFsUri()): T = {
    val uri = new URI("jar", fsUri.toString(), null);
    val env = new HashMap[String, String]();
    env.put("create", "true");
    val fs = FileSystems.newFileSystem(uri, env);
    val p = os.root("/", fs)
    try {
      os.makeDir(p / "test")
      os.makeDir(p / "test" / "dir")
      f(fs)
    } finally {
      cleanUpFs(fs, fsUri)
    }
  }

  def cleanUpFs(fs: FileSystem, fsUri: URI): Unit = {
    fs.close()
    os.remove(Path(fsUri))
  }

  val testsCommon = Tests { // native doesnt support custom fs yet
    test("customFilesystem") {
      test("createPath") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          assert(p.root == "/")
          assert(p.fileSystem == fileSystem)
        }
      }
      test("list") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test"
          os.makeDir(p / "dir2")
          os.makeDir(p / "dir3")
          assert(os.list(p).size == 3)
        }
      }
      test("removeDir") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir" / "dir2"
          os.makeDir.all(p)
          assert(os.exists(p))
          os.remove.all(p)
          assert(!os.exists(p))
        }
      }
      test("failTemp") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          intercept[UnsupportedOperationException] {
            os.temp.dir(dir = p)
          }
        }
      }
      test("failProcCall") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          intercept[UnsupportedOperationException] {
            os.proc("echo", "hello").call(cwd = p)
          }
        }
      }
      test("up") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          assert((p / os.up) == os.root("/", fileSystem) / "test")
        }
      }
      test("withRelPath") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          val rel = os.rel / os.up / "file.txt"
          assert((p / rel) == os.root("/", fileSystem) / "test" / "file.txt")
        }
      }
      test("withSubPath") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          val sub = os.sub / "file.txt"
          assert((p / sub) == os.root("/", fileSystem) / "test" / "dir" / "file.txt")
        }
      }
      test("differentFsCompare") {
        withCustomFs { fs1 =>
          withCustomFs(
            { fs2 =>
              val p1 = os.root("/", fs1) / "test" / "dir"
              val p2 = os.root("/", fs2) / "test" / "dir"
              assert(p1 != p2)
            },
            fsUri = customFsUri("bar.jar")
          )
        }
      }
      test("failRelativeToDifferentFs") {
        withCustomFs { fs1 =>
          withCustomFs(
            { fs2 =>
              val p1 = os.root("/", fs1) / "test" / "dir"
              val p2 = os.root("/", fs2) / "test" / "dir"
              intercept[IllegalArgumentException] {
                p1.relativeTo(p2)
              }
            },
            fsUri = customFsUri("bar.jar")
          )
        }
      }
      test("failSubRelativeToDifferentFs") {
        withCustomFs { fs1 =>
          withCustomFs(
            { fs2 =>
              val p1 = os.root("/", fs1) / "test" / "dir"
              val p2 = os.root("/", fs2) / "test" / "dir"
              intercept[IllegalArgumentException] {
                p1.subRelativeTo(p2)
              }
            },
            fsUri = customFsUri("bar.jar")
          )
        }
      }
    }
  }

  val testsJava11 = Tests {
    test("customFilesystem") {
      test("writeAndRead") {
        withCustomFs { fileSystem =>
          val p = root("/", fileSystem) / "test" / "dir"
          os.write(p / "file.txt", "Hello")
          assert(os.read(p / "file.txt") == "Hello")
        }
      }
      test("writeOver") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          os.write(p / "file.txt", "Hello World")
          os.write.over(p / "file.txt", "Hello World2")
          assert(os.read(p / "file.txt") == "Hello World2")
        }
      }
      test("move") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          os.write(p / "file.txt", "Hello World")
          os.move(p / "file.txt", p / "file2.txt")
          assert(os.read(p / "file2.txt") == "Hello World")
          assert(!os.exists(p / "file.txt"))
        }
      }
      test("copy") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          os.write(p / "file.txt", "Hello World")
          os.copy(p / "file.txt", p / "file2.txt")
          assert(os.read(p / "file2.txt") == "Hello World")
          assert(os.exists(p / "file.txt"))
        }
      }
      test("copyAndMergeToRootDirectoryWithCreateFolders") {
        withCustomFs { fileSystem =>
          val root = os.root("/", fileSystem)
          val file = root / "test" / "dir" / "file.txt"
          os.write(file, "Hello World")
          os.copy(root / "test" / "dir", root, createFolders = true, mergeFolders = true)
          assert(os.read(root / "file.txt") == "Hello World")
          assert(os.exists(root / "file.txt"))
        }
      }
      test("failMoveToRootDirectoryWithCreateFolders") {
        withCustomFs { fileSystem =>
          val root = os.root("/", fileSystem)
          // This should fail. Just test that it doesn't throw PathError.AbsolutePathOutsideRoot.
          intercept[FileAlreadyExistsException] {
            os.move(root / "test" / "dir", root, createFolders = true)
          }
        }
      }
      test("copyMatchingAndMergeToRootDirectory") {
        withCustomFs { fileSystem =>
          val root = os.root("/", fileSystem)
          val file = root / "test" / "dir" / "file.txt"
          os.write(file, "Hello World")
          os.list(root / "test").collect(os.copy.matching(mergeFolders = true) {
            case p / "test" / _ => p
          })
          assert(os.read(root / "file.txt") == "Hello World")
          assert(os.exists(root / "file.txt"))
        }
      }
      test("failMoveMatchingToRootDirectory") {
        withCustomFs { fileSystem =>
          // can't use a `intercept`, see https://github.com/com-lihaoyi/os-lib/pull/267#issuecomment-2116131445
          Try {
            os.list(os.root("/", fileSystem)).collect(os.move.matching { case p / "test" => p })
          } match {
            // This is expected. We just test that it doesn't throw PathError.AbsolutePathOutsideRoot.
            case Failure(e @(_: IllegalArgumentException | _: FileAlreadyExistsException)) => e.getMessage
          }
        }
      }
      test("remove") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          os.write(p / "file.txt", "Hello World")
          assert(os.exists(p / "file.txt"))
          os.remove(p / "file.txt")
          assert(!os.exists(p / "file.txt"))
        }
      }
      test("removeAll") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          os.write(p / "file.txt", "Hello World")
          os.write(p / "file2.txt", "Hello World")
          os.remove.all(p)
          assert(!os.exists(p / "file.txt"))
          assert(!os.exists(p / "file2.txt"))
        }
      }
      test("failSymlink") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          os.write(p / "file.txt", "Hello World")
          intercept[UnsupportedOperationException] {
            os.symlink(p / "link", p / "file.txt")
          }
        }
      }
      test("walk") {
        withCustomFs { fileSystem =>
          val p = os.root("/", fileSystem) / "test" / "dir"
          os.write(p / "file.txt", "Hello World")
          os.write(p / "file2.txt", "Hello World")
          os.write(p / "file3.txt", "Hello World")
          os.makeDir(p / "dir2")
          os.write(p / "dir2" / "file.txt", "Hello World")
          assert(os.walk(p).map(_.relativeTo(p)).toSet ==
            Set(
              RelPath("file.txt"),
              RelPath("file2.txt"),
              RelPath("file3.txt"),
              RelPath("dir2"),
              RelPath("dir2/file.txt")
            ))
        }
      }
    }
  }

  val testWindows = Tests {
    test("cRootPath") {
      val p1 = os.root("C:\\") / "Users"
      assert(p1.toString == "C:\\Users")
      val p2 = os.root("C:/") / "Users"
      assert(p2.toString == "C:\\Users")
    }
  }

  private lazy val isWindows: Boolean = {
    sys.props("os.name").toLowerCase().contains("windows")
  }

  private lazy val isJava11OrAbove: Boolean = {
    val version = System.getProperty("java.version")
    val major = version.split("\\.")(0).toInt
    major >= 11
  }

  override val tests: Tests =
    testsCommon ++ (if (isJava11OrAbove) testsJava11 else Tests {}) ++ (if (isWindows) testWindows
                                                                        else Tests {})
}
