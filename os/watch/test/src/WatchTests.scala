package test.os.watch

import scala.util.Properties.isWin
import scala.util.Random
import utest._

object WatchTests extends TestSuite with TestSuite.Retries {
  override val utestRetryCount =
    if (sys.env.get("CI").contains("true")) {
      if (sys.env.get("RUNNER_OS").contains("macOS")) 10
      else 3
    } else {
      0
    }

  val tests = Tests {
    test("singleFolder") - _root_.test.os.TestUtil.prep { wd =>
      val changedPaths = collection.mutable.Set.empty[os.Path]
      _root_.os.watch.watch(
        Seq(wd),
        onEvent = _.foreach(changedPaths.add)
      )

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

    test("openClose") {
      _root_.test.os.TestUtil.prep { wd =>
        println("openClose in " + wd)
        for (index <- Range(0, 200)) {
          println("watch index " + index)
          @volatile var done = false
          val res = os.watch.watch(
            Seq(wd),
            filter = _ => true,
            onEvent = path => {
              println(path);
              done = true
            },
            logger = (event, data) => println(event)
          )
          Thread.sleep(10)
          os.write.append(wd / s"file.txt", "" + index)
          try {
            while (!done) Thread.sleep(1)
          } finally res.close()
        }
      }
    }
  }
}
