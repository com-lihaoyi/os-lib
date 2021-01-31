package test.os.watch

import utest._
object WatchTests extends TestSuite{

  val tests = Tests {
    test("singleFolder") - _root_.test.os.TestUtil.prep{wd => if (_root_.test.os.Unix()){
      val changedPaths = collection.mutable.Set.empty[os.Path]
      _root_.os.watch.watch(
        Seq(wd),
        onEvent = _.foreach(changedPaths.add),
//        (s, v) => println(s + " " + v)
      )

//      os.write(wd / "lols", "")
//      Thread.sleep(100)

      changedPaths.clear()

      def checkFileManglingChanges(p: os.Path) = {

        checkChanges(
          os.write(p, ""),
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
        assert(expectedChangedPaths == changedSubPaths)
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

      checkFileManglingChanges(wd / "my-new-folder" / "test")

      checkChanges(
        os.move(wd / "folder2", wd / "folder3"),
        Set(
          os.sub / "folder2",
          os.sub / "folder3",

          os.sub / "folder3" / "nestedA",
          os.sub / "folder3" / "nestedA" / "a.txt",
          os.sub / "folder3" / "nestedB",
          os.sub / "folder3" / "nestedB" / "b.txt",
        )
      )

      checkChanges(
        os.copy(wd / "folder3", wd / "folder4"),
        Set(
          os.sub / "folder4",
          os.sub / "folder4" / "nestedA",
          os.sub / "folder4" / "nestedA" / "a.txt",
          os.sub / "folder4" / "nestedB",
          os.sub / "folder4" / "nestedB" / "b.txt"
        )
      )

      checkChanges(
        os.remove.all(wd / "folder4"),
        Set(
          os.sub / "folder4",
          os.sub / "folder4" / "nestedA",
          os.sub / "folder4" / "nestedA" / "a.txt",
          os.sub / "folder4" / "nestedB",
          os.sub / "folder4" / "nestedB" / "b.txt"
        )
      )

      checkFileManglingChanges(wd / "folder3" / "nestedA" / "double-nested-file")
      checkFileManglingChanges(wd / "folder3" / "nestedB" / "double-nested-file")

      checkChanges(
        os.symlink(wd / "newlink", wd / "doesntexist"),
        Set(os.sub / "newlink")
      )

      checkChanges(
        os.symlink(wd / "newlink2", wd / "folder3"),
        Set(os.sub / "newlink2")
      )

      checkChanges(
        os.hardlink(wd / "newlink3", wd / "folder3" / "nestedA" / "a.txt"),
        System.getProperty("os.name") match{
          case "Linux" => Set(os.sub / "newlink3")
          case "Mac OS X" =>
            Set(
              os.sub / "newlink3",
              os.sub / "folder3" / "nestedA",
              os.sub / "folder3" / "nestedA" / "a.txt",
            )
        }
      )
    }}
  }
}
