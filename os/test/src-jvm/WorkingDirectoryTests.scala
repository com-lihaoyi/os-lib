package test.os

import test.os.TestUtil.prep
import utest._

object WorkingDirectoryTests extends TestSuite {
  def tests = Tests{
    test("change"){
      if(Unix()){
        val posix = jnr.posix.POSIXFactory.getPOSIX()
        os.setCwdSupplier(() => os.Path(posix.getcwd()))

        val initialWd = os.pwd

        val expectedWd = os.Path("/var/tmp/os-lib-wd-test")
        os.makeDir.all(expectedWd)

        val res = posix.chdir(expectedWd.toString)
        assert(res == 0)

        val finalWd = os.pwd
        assert(expectedWd == finalWd)
      }
    }
  }
}
