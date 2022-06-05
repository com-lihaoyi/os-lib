package os.watch.inotify

import utest.{TestSuite, Tests, test}
import java.util.concurrent.atomic.AtomicReference


object NotifyTests extends TestSuite {
  val tests = Tests {

    test("Basic") {
      if (System.getProperty("os.name") == "Linux") {
        import Notify.it

        val temp = os.temp.dir(deleteOnExit = true)
        val fd = it.inotify_init()

        val masks = Mask.create | Mask.delete | Mask.move | Mask.modify

        def watch(path: os.Path): os.Path = {
          Notify.add_watch(fd, path, masks)
          path
        }

        watch(temp)

        def makeDir(path: os.Path): os.Path = {
          os.makeDir(path)
          watch(path)
        }

        def makeFile(path: os.Path): os.Path = {
          os.write(path, path.last)
          path
        }

        val r = makeDir(temp / "r")

        Seq("a", "b", "c").foreach { n =>
          val dir_path = makeDir(r / n)
          val file_path = makeFile(dir_path / n)
        }

        os.remove.all(temp)

        var i = 0

        Notify.events(new AtomicReference(Option(fd))).generate { e =>
          println(s"[$i] $e")
          i += 1
          if (i == 22) {
            os.Generator.End
          } else {
            os.Generator.Continue
          }
        }
      }
    }
  }
}
