package test.os.watch

import os.Path

import scala.util.Properties.isWin
import scala.util.Random
import utest._

import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.duration._

object WatchTests extends TestSuite with TestSuite.Retries {
  override val utestRetryCount =
    if (sys.env.get("CI").contains("true")) {
      if (sys.env.get("RUNNER_OS").contains("macOS")) 10
      else 3
    } else {
      0
    }

  val tests = Tests {
    test("nonExistentFolder") - _root_.test.os.TestUtil.prep { wd =>
      intercept[IllegalArgumentException] {
        _root_.os.watch.watch(Seq(wd / "does-not-exist"), onEvent = _ => ())
      }
    }

    test("emptyFolder") - _root_.test.os.TestUtil.prep { wd =>
      val folder = wd / "empty"
      os.makeDir.all(folder)
      val filesBefore = os.list(folder)
      assert(filesBefore.isEmpty)
      val watcher = _root_.os.watch.watch(Seq(folder), onEvent = _ => ())
      val files = os.list(folder)
      assert(files.isEmpty)
      watcher.close()
    }

    test("singleFolder") - _root_.test.os.TestUtil.prep { wd =>
      val changedPaths = collection.mutable.Set.empty[os.Path]
      val watcher = _root_.os.watch.watch(
        Seq(wd),
        onEvent = _.foreach(changedPaths.add),
        //        logger = (str, value) => println(s"$str $value")
      )

      try {
        //      os.write(wd / "lols", "")
        //      Thread.sleep(100)

        changedPaths.clear()

        def checkFileManglingChanges(p: os.Path) = {

          checkChanges(
            os.write(p, Random.nextString(100)),
            Set(p.subRelativeTo(wd))
          )

          checkChanges(
            os.write.append(p, "hello"),
            Set(p.subRelativeTo(wd))
          )

          checkChanges(
            os.write.over(p, "world"),
            Set(p.subRelativeTo(wd))
          )

          checkChanges(
            os.truncate(p, 1),
            Set(p.subRelativeTo(wd))
          )

          checkChanges(
            os.remove(p),
            Set(p.subRelativeTo(wd))
          )
        }

        def checkChanges(action: => Unit, expectedChangedPaths: Set[os.SubPath]) = synchronized {
          changedPaths.clear()
          action
          Thread.sleep(200)
          val changedSubPaths = changedPaths.map(_.subRelativeTo(wd))
          // on Windows sometimes we get more changes
          if (isWin) assert(expectedChangedPaths.subsetOf(changedSubPaths))
          else assert(expectedChangedPaths == changedSubPaths)
        }

        checkFileManglingChanges(wd / "test")

        checkChanges(
          os.remove(wd / "File.txt"),
          Set(os.sub / "File.txt")
        )

        checkChanges(
          os.makeDir(wd / "my-new-folder"),
          Set(os.sub / "my-new-folder")
        )

        checkFileManglingChanges(wd / "my-new-folder/test")

        locally {
          val expectedChanges = if (isWin) Set(
            os.sub / "folder2",
            os.sub / "folder3"
          )
          else Set(
            os.sub / "folder2",
            os.sub / "folder3",
            os.sub / "folder3/nestedA",
            os.sub / "folder3/nestedA/a.txt",
            os.sub / "folder3/nestedB",
            os.sub / "folder3/nestedB/b.txt"
          )
          checkChanges(
            os.move(wd / "folder2", wd / "folder3"),
            expectedChanges
          )
        }

        checkChanges(
          os.copy(wd / "folder3", wd / "folder4"),
          Set(
            os.sub / "folder4",
            os.sub / "folder4/nestedA",
            os.sub / "folder4/nestedA/a.txt",
            os.sub / "folder4/nestedB",
            os.sub / "folder4/nestedB/b.txt"
          )
        )

        checkChanges(
          os.remove.all(wd / "folder4"),
          Set(
            os.sub / "folder4",
            os.sub / "folder4/nestedA",
            os.sub / "folder4/nestedA/a.txt",
            os.sub / "folder4/nestedB",
            os.sub / "folder4/nestedB/b.txt"
          )
        )

        checkFileManglingChanges(wd / "folder3/nestedA/double-nested-file")
        checkFileManglingChanges(wd / "folder3/nestedB/double-nested-file")

        checkChanges(
          os.symlink(wd / "newlink", wd / "doesntexist"),
          Set(os.sub / "newlink")
        )

        checkChanges(
          os.symlink(wd / "newlink2", wd / "folder3"),
          Set(os.sub / "newlink2")
        )

        checkChanges(
          os.hardlink(wd / "newlink3", wd / "folder3/nestedA/a.txt"),
          System.getProperty("os.name") match {
            case "Mac OS X" =>
              Set(
                os.sub / "newlink3",
                os.sub / "folder3/nestedA",
                os.sub / "folder3/nestedA/a.txt"
              )
            case _ => Set(os.sub / "newlink3")
          }
        )
      }
      finally {
        watcher.close()
      }
    }

    def createManyFilesInManyFolders(wd: Path, numPaths: Int) = {
      val rng = new Random(100)
      val paths = generateNRandomPaths(numPaths, wd, random = rng)
      val directories = paths.iterator.map(_.toNIO.getParent.toAbsolutePath).toSet
      directories.foreach(dir => os.makeDir.all.apply(Path(dir)))
      paths.foreach(p => os.write.over(p, rng.nextString(100)))
      paths
    }

    def testManyFilesInManyFolders(wd: Path, paths: Vector[Path]): Unit = {
      val changedPaths = collection.mutable.Set.empty[os.Path]

      def waitUntilFinished(): Unit = {
        val timeout = 500
        //        print("Waiting for events to stop coming")
        //        System.out.flush()
        var last = changedPaths.size
        Thread.sleep(timeout)
        var current = last
        while ({ current = changedPaths.size; last != current }) {
          last = current
          //          print(".")
          //          System.out.flush()
          Thread.sleep(timeout)
        }
        //        println(" Done.")
      }

      //      println(s"Watching $wd")
      val watcher = os.watch.watch(
        Seq(wd),
        onEvent = paths => changedPaths ++= paths,
        //        logger = (evt, data) => println(s"$evt $data")
      )
      try {
        // On mac os if you create a bunch of files and then start watching the directory
        // AFTER those files are created, you will get events about those files.
        //
        // Which makes no sense, but it is what it is. Thus we wait until we aren't getting
        // any more events and then make sure to clear the set before actually running our test.
        waitUntilFinished()
        changedPaths.clear()

        val willChange = paths.iterator.take(paths.size / 2).toSet
        willChange.foreach(p => os.write.over(p, "changed"))
        waitUntilFinished()

        val unexpectedChanges = changedPaths.toSet -- willChange
        val unexpectedChangeCount = unexpectedChanges.size
        assert(unexpectedChangeCount == 0)

        val missingChanges = willChange -- changedPaths
        val missingChangeCount = missingChanges.size
        assert(missingChangeCount == 0)
      }
      finally {
        watcher.close()
      }
    }

    test("manyFiles") {
      test("inManyFoldersSmall") - _root_.test.os.TestUtil.prep { wd =>
        val paths = createManyFilesInManyFolders(wd, numPaths = 1000)
        testManyFilesInManyFolders(wd, paths)
      }

      test("inManyFoldersMedium") - _root_.test.os.TestUtil.prep { wd =>
        val paths = createManyFilesInManyFolders(wd, numPaths = 5000)
        testManyFilesInManyFolders(wd, paths)
      }

      test("inManyFoldersLarge") - _root_.test.os.TestUtil.prep { wd =>
        val paths = createManyFilesInManyFolders(wd, numPaths = 10000)
        testManyFilesInManyFolders(wd, paths)
      }

      test("inManyFoldersLargest") - _root_.test.os.TestUtil.prep { wd =>
        val numPaths = 12000 // My Linux machine starts overflowing and losing events at 13k files.
        val paths = createManyFilesInManyFolders(wd, numPaths)
        testManyFilesInManyFolders(wd, paths)
      }

      test("inManyFoldersThreaded") - _root_.test.os.TestUtil.prep { wd =>
        import scala.concurrent.ExecutionContext.Implicits.global

        val numPaths = 1000
        val futures = (0 to 100).map { idx =>
          Future {
            val myWd = wd / s"job-$idx"
            val paths = createManyFilesInManyFolders(myWd, numPaths)
            testManyFilesInManyFolders(myWd, paths)
          }
        }
        futures.foreach(Await.result(_, 20.seconds))
      }

      test("inManyFoldersThreadedSequential") - _root_.test.os.TestUtil.prep { wd =>
        import scala.concurrent.ExecutionContext.Implicits.global

        val numPaths = 1000
        val lock = new Object
        val futures = (0 to 100).map { idx =>
          Future {
            val myWd = wd / s"job-$idx"
            val paths = createManyFilesInManyFolders(myWd, numPaths)
            lock.synchronized {
              Future(testManyFilesInManyFolders(myWd, paths))
            }
          }
        }
        futures.foreach(Await.result(_, 20.seconds))
      }
    }

    def testOpenClose(wd: os.Path, count: Int): Unit = {
      println("openClose in " + wd)
      for (index <- Range(0, count)) {
        println("watch index " + index)
        @volatile var done = false
        val res = os.watch.watch(
          Seq(wd),
          filter = _ => true,
          onEvent = path => {
            println(path)
            done = true
          },
          logger = (event, data) => println(event)
        )
        os.write.append(wd / s"file.txt", "" + index)

        val startTimeNanos = System.nanoTime()
        val timeout = 3.seconds
        val timeoutNanos = timeout.toNanos
        try {
          while (!done) {
            val taken = System.nanoTime() - startTimeNanos
            if (taken >= timeoutNanos)
              throw new TimeoutException(s"no file system changes detected within $timeout")
            Thread.sleep(1)
          }
        } finally res.close()
      }
    }

    test("openClose") {
      test("once") {
        _root_.test.os.TestUtil.prep(testOpenClose(_, 1))
      }

      test("manyTimes") {
        _root_.test.os.TestUtil.prep(testOpenClose(_, 200))
      }
    }
  }

  /**
   * Generates N random paths, arbitrarily nested under a given subdirectory.
   *
   * @param count            The number of random paths to generate.
   * @param baseSubdirectory Subdirectory under which paths will be generated.
   * @param maxNestingDepth  The maximum number of directory levels (0 means files directly in baseSubdirectory).
   * @return A Vector of strings, where each string is a fully formed random path.
   * @throws IllegalArgumentException if N is negative, or maxNestingDepth is negative.
   */
  def generateNRandomPaths(
    count: Int,
    baseSubdirectory: Path,
    maxNestingDepth: Int = 5,
    random: Random
  ): Vector[Path] = {
    def randomAlphanumeric(length: Int): String =
      random.alphanumeric.take(length).mkString

    def generateSingleRandomPath(baseDir: Path) = {
      // actualNestingDepth can be 0 (file directly in baseDir) up to maxNestingDepth
      val actualNestingDepth = random.nextInt(maxNestingDepth + 1)

      var currentPath: Path = baseDir

      // Create random subdirectories
      for (_ <- 0 until actualNestingDepth) {
        currentPath = currentPath / randomAlphanumeric(3).toLowerCase
      }

      // Create random filename with extension
      val fileName = s"${randomAlphanumeric(8)}.${randomAlphanumeric(3).toLowerCase}"
      currentPath = currentPath / fileName

      currentPath
    }

    if (count < 0) throw new IllegalArgumentException("Number of paths cannot be negative.")
    if (maxNestingDepth < 0) throw new IllegalArgumentException("maxNestingDepth cannot be negative.")

    Vector.fill(count)(generateSingleRandomPath(baseSubdirectory))
  }
}
