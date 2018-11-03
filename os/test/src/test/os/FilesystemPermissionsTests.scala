package test.os

import test.os.TestUtil.prep
import utest._

object FilesystemPermissionsTests extends TestSuite {
  def tests = Tests{
    'perms - {
      * - prep { wd =>
        os.perms.set(wd / "File.txt", "rwxrwxrwx")
        os.perms(wd / "File.txt").toString() ==> "rwxrwxrwx"
        os.perms(wd / "File.txt").toInt() ==> Integer.parseInt("777", 8)

        os.perms.set(wd / "File.txt", Integer.parseInt("755", 8))
        os.perms(wd / "File.txt").toString() ==> "rwxr-xr-x"

        os.perms.set(wd / "File.txt", "r-xr-xr-x")
        os.perms.set(wd / "File.txt", Integer.parseInt("555", 8))
      }
    }
    'owner - {
      * - prep { wd =>

      }
      'set - {
        * - prep { wd =>

        }
      }
    }
    'group - {
      * - prep { wd =>

      }
      'set - {
        * - prep { wd =>

        }
      }
    }
  }
}
