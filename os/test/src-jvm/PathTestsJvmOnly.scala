package test.os

import java.nio.file.Paths

import os._
import utest._
object PathTestsJvmOnly extends TestSuite{
  val tests = Tests {
    test("construction"){
      test("symlinks"){

        val names = Seq('test123, 'test124, 'test125, 'test126)
        val twd = temp.dir()

        test("nestedSymlinks"){
          if(Unix()) {
            names.foreach(p => os.remove.all(twd/p))
            os.makeDir.all(twd/'test123)
            os.symlink(twd/'test124, twd/'test123)
            os.symlink(twd/'test125, twd/'test124)
            os.symlink(twd/'test126, twd/'test125)
            assert(followLink(twd/'test126).get == followLink(twd/'test123).get)
            names.foreach(p => os.remove(twd/p))
            names.foreach(p => assert(!exists(twd/p)))
          }
        }

        test("danglingSymlink"){
          if(Unix()) {
            names.foreach(p => os.remove.all(twd/p))
            os.makeDir.all(twd/'test123)
            os.symlink(twd/'test124, twd/'test123)
            os.symlink(twd/'test125, twd/'test124)
            os.symlink(twd/'test126, twd/'test125)
            os.remove(twd / 'test123)
            assert(followLink(twd / 'test126).isEmpty)
            names.foreach(p => os.remove.all(twd / p))
            names.foreach(p => assert(!exists(twd / p)))
            names.foreach(p => os.remove.all(twd/p))
            names.foreach(p => assert(!exists(twd/p)))
          }
        }
      }
    }
  }
}
