package test.os.watch

import utest._
object WatchTests extends TestSuite{

  val tests = Tests {
    test("singleFolder") - _root_.test.os.TestUtil.prep{wd => if (_root_.test.os.Unix()){
      val changedPaths = collection.mutable.Set.empty[os.Path]
      _root_.os.watch.watch(
        Seq(wd),
        onEvent = _.foreach(changedPaths.add)
      )

      def checkFileManglingChanges(p: os.Path) = {

        checkChanges(
          os.write(p, ""),
          Set(p)
        )

        checkChanges(
          os.write.append(p, "hello"),
          Set(p)
        )

        checkChanges(
          os.write.over(p, "world"),
          Set(p)
        )

        checkChanges(
          os.truncate(p, 1),
          Set(p)
        )

        checkChanges(
          os.remove(p),
          Set(p)
        )
      }
      def checkChanges(action: => Unit, expectedChangedPaths: Set[os.Path]) = {

        action
        Thread.sleep(100)
        assert(expectedChangedPaths == changedPaths)
        changedPaths.clear()
      }

      checkFileManglingChanges(wd / "test")

      checkChanges(
        os.remove(wd / "File.txt"),
        Set(wd / "File.txt")
      )

      checkChanges(
        os.makeDir(wd / "my-new-folder"),
        Set(wd / "my-new-folder")
      )

      checkFileManglingChanges(wd / "my-new-folder" / "test")

      checkChanges(
        os.move(wd / "folder2", wd / "folder3"),
        Set(
          wd / "folder2",
          wd / "folder3",

          wd / "folder3" / "nestedA",
          wd / "folder3" / "nestedA" / "a.txt",
          wd / "folder3" / "nestedB",
          wd / "folder3" / "nestedB" / "b.txt",
        )
      )

      checkChanges(
        os.copy(wd / "folder3", wd / "folder4"),
        Set(
          wd / "folder4",
          wd / "folder4" / "nestedA",
          wd / "folder4" / "nestedA" / "a.txt",
          wd / "folder4" / "nestedB",
          wd / "folder4" / "nestedB" / "b.txt"
        )
      )

      checkChanges(
        os.remove.all(wd / "folder4"),
        Set(
          wd / "folder4",
          wd / "folder4" / "nestedA",
          wd / "folder4" / "nestedA" / "a.txt",
          wd / "folder4" / "nestedB",
          wd / "folder4" / "nestedB" / "b.txt"
        )
      )

      checkFileManglingChanges(wd / "folder3" / "nestedA" / "double-nested-file")
      checkFileManglingChanges(wd / "folder3" / "nestedB" / "double-nested-file")

      checkChanges(
        os.symlink(wd / "newlink", wd / "doesntexist"),
        Set(wd / "newlink")
      )

      checkChanges(
        os.symlink(wd / "newlink2", wd / "folder3"),
        Set(wd / "newlink2")
      )

      checkChanges(
        os.hardlink(wd / "newlink3", wd / "folder3" / "nestedA" / "a.txt"),
        Set(
          wd / "newlink3",
          wd / "folder3" / "nestedA",
          wd / "folder3" / "nestedA" / "a.txt",
        )
      )
    }}
  }
}
