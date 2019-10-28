package test.os.watch

import java.nio.file.attribute.{GroupPrincipal, FileTime}

import utest._
object WatchTests extends TestSuite{

  val tests = Tests {
    test("singleFolder") - _root_.test.os.TestUtil.prep{wd => if (_root_.test.os.Unix()){
      val changedPaths = collection.mutable.Set.empty[os.Path]
      _root_.os.watch.watch(
        Seq(wd),
        onEvent = _.foreach(changedPaths.add)
      )

      os.write(wd / "test", "")
      os.remove(wd / "File.txt")
      os.move(wd / "folder2", wd / "folder3")
      os.copy(wd / "folder3", wd / "folder4")

      Thread.sleep(100)

      val expected = Set(
        wd / "test",

        wd / "File.txt",

        wd / "folder2",
        wd / "folder3",

        wd / "folder4",
        wd / "folder4" / "nestedA",
        wd / "folder4" / "nestedA" / "a.txt",
        wd / "folder4" / "nestedB",
        wd / "folder4" / "nestedB" / "b.txt"
      )

      assert(changedPaths == expected)
    }}
  }
}
