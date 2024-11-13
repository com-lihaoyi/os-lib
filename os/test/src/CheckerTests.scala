package test.os

import test.os.TestUtil._
import utest._

object CheckerTests extends TestSuite {

  def tests: Tests = Tests {
    // restricted directory
    val rd = os.Path(sys.env("OS_TEST_RESOURCE_FOLDER")) / "restricted"

    test("stat") {
      test("mtime") - prepChecker { wd =>
        val before = os.mtime(rd / "File.txt")
        intercept[WriteDenied] {
          os.mtime.set(rd / "File.txt", 0)
        }
        os.mtime(rd / "File.txt") ==> before

        os.mtime.set(wd / "File.txt", 0)
        os.mtime(wd / "File.txt") ==> 0

        os.mtime.set(wd / "File.txt", 90000)
        os.mtime(wd / "File.txt") ==> 90000
        os.mtime(wd / "misc/file-symlink") ==> 90000

        os.mtime.set(wd / "misc/file-symlink", 70000)
        os.mtime(wd / "File.txt") ==> 70000
        os.mtime(wd / "misc/file-symlink") ==> 70000
        assert(os.mtime(wd / "misc/file-symlink", followLinks = false) != 40000)
      }
    }

    test("perms") {
      test - prepChecker { wd =>
        if (Unix()) {
          val before = os.perms(rd / "File.txt")
          intercept[WriteDenied] {
            os.perms.set(rd / "File.txt", "rwxrwxrwx")
          }
          os.perms(rd / "File.txt") ==> before

          os.perms.set(wd / "File.txt", "rwxrwxrwx")
          os.perms(wd / "File.txt").toString() ==> "rwxrwxrwx"
          os.perms(wd / "File.txt").toInt() ==> Integer.parseInt("777", 8)

          os.perms.set(wd / "File.txt", Integer.parseInt("755", 8))
          os.perms(wd / "File.txt").toString() ==> "rwxr-xr-x"

          os.perms.set(wd / "File.txt", "r-xr-xr-x")
          os.perms.set(wd / "File.txt", Integer.parseInt("555", 8))
        }
      }
      test("owner") - prepChecker { wd =>
        if (Unix()) {
          // Only works as root :(
          if (false) {
            intercept[WriteDenied] {
              os.owner.set(rd / "File.txt", "nobody")
            }

            val originalOwner = os.owner(wd / "File.txt")

            os.owner.set(wd / "File.txt", "nobody")
            os.owner(wd / "File.txt").getName ==> "nobody"

            os.owner.set(wd / "File.txt", originalOwner)
          }
        }
      }
      test("group") - prepChecker { wd =>
        if (Unix()) {
          // Only works as root :(
          if (false) {
            intercept[WriteDenied] {
              os.group.set(rd / "File.txt", "nobody")
            }

            val originalGroup = os.group(wd / "File.txt")

            os.group.set(wd / "File.txt", "nobody")
            os.group(wd / "File.txt").getName ==> "nobody"

            os.group.set(wd / "File.txt", originalGroup)
          }
        }
      }
    }

    test("move") - prepChecker { wd =>
      intercept[WriteDenied] {
        os.move(rd / "folder1/one.txt", wd / "folder1/File.txt")
      }
      os.list(wd / "folder1") ==> Seq(wd / "folder1/one.txt")

      intercept[WriteDenied] {
        os.move(wd / "folder1/one.txt", rd / "folder1/File.txt")
      }
      os.list(rd / "folder1") ==> Seq(rd / "folder1/one.txt")

      intercept[WriteDenied] {
        os.move(wd / "folder2/nestedA", rd / "folder2/nestedC")
      }
      os.list(rd / "folder2") ==> Seq(rd / "folder2/nestedA", rd / "folder2/nestedB")

      os.list(wd / "folder1") ==> Seq(wd / "folder1/one.txt")
      os.move(wd / "folder1/one.txt", wd / "folder1/first.txt")
      os.list(wd / "folder1") ==> Seq(wd / "folder1/first.txt")

      os.list(wd / "folder2") ==> Seq(wd / "folder2/nestedA", wd / "folder2/nestedB")
      os.move(wd / "folder2/nestedA", wd / "folder2/nestedC")
      os.list(wd / "folder2") ==> Seq(wd / "folder2/nestedB", wd / "folder2/nestedC")

      os.read(wd / "File.txt") ==> "I am cow"
      os.move(wd / "Multi Line.txt", wd / "File.txt", replaceExisting = true)
      os.read(wd / "File.txt") ==>
        """I am cow
          |Hear me moo
          |I weigh twice as much as you
          |And I look good on the barbecue""".stripMargin
    }
    test("copy") - prepChecker { wd =>
      intercept[ReadDenied] {
        os.copy(rd / "folder1/one.txt", wd / "folder1/File.txt")
      }
      os.list(wd / "folder1") ==> Seq(wd / "folder1/one.txt")

      intercept[WriteDenied] {
        os.copy(wd / "folder1/one.txt", rd / "folder1/File.txt")
      }
      os.list(rd / "folder1") ==> Seq(rd / "folder1/one.txt")

      intercept[WriteDenied] {
        os.copy(wd / "folder2/nestedA", rd / "folder2/nestedC")
      }
      os.list(rd / "folder2") ==> Seq(rd / "folder2/nestedA", rd / "folder2/nestedB")

      os.list(wd / "folder1") ==> Seq(wd / "folder1/one.txt")
      os.copy(wd / "folder1/one.txt", wd / "folder1/first.txt")
      os.list(wd / "folder1") ==> Seq(wd / "folder1/first.txt", wd / "folder1/one.txt")

      os.list(wd / "folder2") ==> Seq(wd / "folder2/nestedA", wd / "folder2/nestedB")
      os.copy(wd / "folder2/nestedA", wd / "folder2/nestedC")
      os.list(wd / "folder2") ==> Seq(
        wd / "folder2/nestedA",
        wd / "folder2/nestedB",
        wd / "folder2/nestedC"
      )

      os.read(wd / "File.txt") ==> "I am cow"
      os.copy(wd / "Multi Line.txt", wd / "File.txt", replaceExisting = true)
      os.read(wd / "File.txt") ==>
        """I am cow
          |Hear me moo
          |I weigh twice as much as you
          |And I look good on the barbecue""".stripMargin
    }
    test("makeDir") {
      test - prepChecker { wd =>
        intercept[WriteDenied] {
          os.makeDir(rd / "new_folder")
        }
        os.exists(rd / "new_folder") ==> false

        os.exists(wd / "new_folder") ==> false
        os.makeDir(wd / "new_folder")
        os.exists(wd / "new_folder") ==> true
      }
      test("all") - prepChecker { wd =>
        intercept[WriteDenied] {
          os.makeDir.all(rd / "new_folder/inner/deep")
        }
        os.exists(rd / "new_folder") ==> false

        os.exists(wd / "new_folder") ==> false
        os.makeDir.all(wd / "new_folder/inner/deep")
        os.exists(wd / "new_folder/inner/deep") ==> true
      }
    }
    test("remove") {
      test - prepChecker { wd =>
        intercept[WriteDenied] {
          os.remove(rd / "File.txt")
        }
        os.exists(rd / "File.txt") ==> true

        intercept[WriteDenied] {
          os.remove(rd / "folder1")
        }
        os.list(rd / "folder1") ==> Seq(rd / "folder1/one.txt")

        Unchecked.scope(os.makeDir(rd / "folder"), os.remove(rd / "folder")) {
          intercept[WriteDenied] {
            os.remove(rd / "folder")
          }
          os.exists(rd / "folder") ==> true
        }
        os.exists(rd / "folder") ==> false

        os.exists(wd / "File.txt") ==> true
        os.remove(wd / "File.txt")
        os.exists(wd / "File.txt") ==> false

        os.exists(wd / "folder1/one.txt") ==> true
        os.remove(wd / "folder1/one.txt")
        os.remove(wd / "folder1")
        os.exists(wd / "folder1/one.txt") ==> false
        os.exists(wd / "folder1") ==> false
      }
      test("link") - prepChecker { wd =>
        intercept[WriteDenied] {
          os.remove(rd / "misc/file-symlink")
        }
        os.exists(rd / "misc/file-symlink", followLinks = false) ==> true

        intercept[WriteDenied] {
          os.remove(rd / "misc/folder-symlink")
        }
        os.exists(rd / "misc/folder-symlink", followLinks = false) ==> true

        intercept[WriteDenied] {
          os.remove(rd / "misc/broken-symlink")
        }
        os.exists(rd / "misc/broken-symlink", followLinks = false) ==> true
        os.exists(rd / "misc/broken-symlink") ==> true

        os.remove(wd / "misc/file-symlink")
        os.exists(wd / "misc/file-symlink", followLinks = false) ==> false
        os.exists(wd / "File.txt", followLinks = false) ==> true

        os.remove(wd / "misc/folder-symlink")
        os.exists(wd / "misc/folder-symlink", followLinks = false) ==> false
        os.exists(wd / "folder1", followLinks = false) ==> true
        os.exists(wd / "folder1/one.txt", followLinks = false) ==> true

        os.remove(wd / "misc/broken-symlink")
        os.exists(wd / "misc/broken-symlink", followLinks = false) ==> false
      }
      test("all") {
        test - prepChecker { wd =>
          intercept[WriteDenied] {
            os.remove.all(rd / "folder1")
          }
          os.list(rd / "folder1") ==> Seq(rd / "folder1/one.txt")

          os.exists(wd / "folder1/one.txt") ==> true
          os.remove.all(wd / "folder1")
          os.exists(wd / "folder1/one.txt") ==> false
          os.exists(wd / "folder1") ==> false
        }
        test("link") - prepChecker { wd =>
          intercept[WriteDenied] {
            os.remove.all(rd / "misc/file-symlink")
          }
          os.exists(rd / "misc/file-symlink", followLinks = false) ==> true

          intercept[WriteDenied] {
            os.remove.all(rd / "misc/folder-symlink")
          }
          os.exists(rd / "misc/folder-symlink", followLinks = false) ==> true

          intercept[WriteDenied] {
            os.remove.all(rd / "misc/broken-symlink")
          }
          os.exists(rd / "misc/broken-symlink", followLinks = false) ==> true

          os.remove.all(wd / "misc/file-symlink")
          os.exists(wd / "misc/file-symlink", followLinks = false) ==> false

          os.remove.all(wd / "misc/folder-symlink")
          os.exists(wd / "misc/folder-symlink", followLinks = false) ==> false
          os.exists(wd / "folder1", followLinks = false) ==> true
          os.exists(wd / "folder1/one.txt", followLinks = false) ==> true

          os.remove.all(wd / "misc/broken-symlink")
          os.exists(wd / "misc/broken-symlink", followLinks = false) ==> false
        }
      }
    }
    test("hardlink") - prepChecker { wd =>
      intercept[WriteDenied] {
        os.hardlink(wd / "Linked.txt", rd / "File.txt")
      }
      os.exists(wd / "Linked.txt") ==> false

      intercept[WriteDenied] {
        os.hardlink(rd / "Linked.txt", wd / "File.txt")
      }
      os.exists(rd / "Linked.txt") ==> false

      os.hardlink(wd / "Linked.txt", wd / "File.txt")
      os.exists(wd / "Linked.txt")
      os.read(wd / "Linked.txt") ==> "I am cow"
      os.isLink(wd / "Linked.txt") ==> false
    }
    test("symlink") - prepChecker { wd =>
      intercept[WriteDenied] {
        os.symlink(rd / "Linked.txt", wd / "File.txt")
      }
      os.exists(rd / "Linked.txt") ==> false

      intercept[WriteDenied] {
        os.symlink(rd / "Linked.txt", os.rel / "File.txt")
      }
      os.exists(rd / "Linked.txt") ==> false

      intercept[WriteDenied] {
        os.symlink(rd / "LinkedFolder1", wd / "folder1")
      }
      os.exists(rd / "LinkedFolder1") ==> false

      intercept[WriteDenied] {
        os.symlink(rd / "LinkedFolder2", os.rel / "folder1")
      }
      os.exists(rd / "LinkedFolder2") ==> false

      os.symlink(wd / "Linked.txt", wd / "File.txt")
      os.read(wd / "Linked.txt") ==> "I am cow"
      os.isLink(wd / "Linked.txt") ==> true

      os.symlink(wd / "Linked2.txt", os.rel / "File.txt")
      os.read(wd / "Linked2.txt") ==> "I am cow"
      os.isLink(wd / "Linked2.txt") ==> true

      os.symlink(wd / "LinkedFolder1", wd / "folder1")
      os.walk(wd / "LinkedFolder1", followLinks = true) ==> Seq(wd / "LinkedFolder1/one.txt")
      os.isLink(wd / "LinkedFolder1") ==> true

      os.symlink(wd / "LinkedFolder2", os.rel / "folder1")
      os.walk(wd / "LinkedFolder2", followLinks = true) ==> Seq(wd / "LinkedFolder2/one.txt")
      os.isLink(wd / "LinkedFolder2") ==> true
    }
    test("temp") {
      test - prepChecker { wd =>
        val before = os.walk(rd)
        intercept[WriteDenied] {
          os.temp("default content", dir = rd)
        }
        os.walk(rd) ==> before

        val tempOne = os.temp("default content", dir = wd)
        os.read(tempOne) ==> "default content"
        os.write.over(tempOne, "Hello")
        os.read(tempOne) ==> "Hello"
      }
      test("dir") - prepChecker { wd =>
        val before = os.walk(rd)
        intercept[WriteDenied] {
          os.temp.dir(dir = rd)
        }
        os.walk(rd) ==> before

        val tempDir = os.temp.dir(dir = wd)
        os.list(tempDir) ==> Nil
        os.write(tempDir / "file", "Hello")
        os.list(tempDir) ==> Seq(tempDir / "file")
      }
    }

    test("read") {
      test("inputStream") - prepChecker { wd =>
        os.exists(rd / "File.txt") ==> true
        intercept[ReadDenied] {
          os.read.inputStream(rd / "File.txt")
        }

        val is = os.read.inputStream(wd / "File.txt") // ==> "I am cow"
        is.read() ==> 'I'
        is.read() ==> ' '
        is.read() ==> 'a'
        is.read() ==> 'm'
        is.read() ==> ' '
        is.read() ==> 'c'
        is.read() ==> 'o'
        is.read() ==> 'w'
        is.read() ==> -1
        is.close()
      }
    }
    test("write") {
      test - prepChecker { wd =>
        intercept[WriteDenied] {
          os.write(rd / "New File.txt", "New File Contents")
        }
        os.exists(rd / "New File.txt") ==> false

        os.write(wd / "New File.txt", "New File Contents")
        os.read(wd / "New File.txt") ==> "New File Contents"

        os.write(wd / "NewBinary.bin", Array[Byte](0, 1, 2, 3))
        os.read.bytes(wd / "NewBinary.bin") ==> Array[Byte](0, 1, 2, 3)
      }
      test("outputStream") - prepChecker { wd =>
        intercept[WriteDenied] {
          os.write.outputStream(rd / "New File.txt")
        }
        os.exists(rd / "New File.txt") ==> false

        val out = os.write.outputStream(wd / "New File.txt")
        out.write('H')
        out.write('e')
        out.write('l')
        out.write('l')
        out.write('o')
        out.close()

        os.read(wd / "New File.txt") ==> "Hello"
      }
    }
    test("truncate") - prepChecker { wd =>
      intercept[WriteDenied] {
        os.truncate(rd / "File.txt", 4)
      }
      Unchecked(os.read(rd / "File.txt")) ==> "I am a restricted cow"

      os.read(wd / "File.txt") ==> "I am cow"

      os.truncate(wd / "File.txt", 4)
      os.read(wd / "File.txt") ==> "I am"
    }

    test("zip") - prepChecker { wd =>
      intercept[WriteDenied] {
        os.zip(
          dest = rd / "zipped.zip",
          sources = Seq(
            wd / "File.txt",
            wd / "folder1"
          )
        )
      }
      os.exists(rd / "zipped.zip") ==> false

      intercept[ReadDenied] {
        os.zip(
          dest = wd / "zipped.zip",
          sources = Seq(
            wd / "File.txt",
            rd / "folder1"
          )
        )
      }
      os.exists(wd / "zipped.zip") ==> false

      val zipFile = os.zip(
        wd / "zipped.zip",
        Seq(
          wd / "File.txt",
          wd / "folder1"
        )
      )

      val unzipDir = os.unzip(zipFile, wd / "unzipped")
      os.walk(unzipDir).sorted ==> Seq(
        unzipDir / "File.txt",
        unzipDir / "folder1/one.txt",
        unzipDir / "folder1"
      ).sorted
    }
    test("unzip") - prepChecker { wd =>
      val zipFileName = "zipped.zip"
      val zipFile: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          wd / "File.txt",
          wd / "folder1"
        )
      )

      intercept[WriteDenied] {
        os.unzip(
          source = zipFile,
          dest = rd / "unzipped"
        )
      }
      os.exists(rd / "unzipped") ==> false

      val unzipDir = os.unzip(
        source = zipFile,
        dest = wd / "unzipped"
      )
      os.walk(unzipDir).length ==> 3
    }
  }
}
