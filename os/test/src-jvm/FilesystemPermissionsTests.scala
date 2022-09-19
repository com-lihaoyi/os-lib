package test.os

import test.os.TestUtil.prep
import utest._

object FilesystemPermissionsTests extends TestSuite {
  def tests = Tests{
    test("perms"){
      test - prep { wd => if (Unix()){
        os.perms.set(wd / "File.txt", "rwxrwxrwx")
        os.perms(wd / "File.txt").toString() ==> "rwxrwxrwx"
        os.perms(wd / "File.txt").toInt() ==> Integer.parseInt("777", 8)

        os.perms.set(wd / "File.txt", Integer.parseInt("755", 8))
        os.perms(wd / "File.txt").toString() ==> "rwxr-xr-x"

        os.perms.set(wd / "File.txt", "r-xr-xr-x")
        os.perms.set(wd / "File.txt", Integer.parseInt("555", 8))
      }}
    }
    test("owner"){
      test - prep { wd => if (Unix()){
        // Only works as root :(
        if(false){
          val originalOwner = os.owner(wd / "File.txt")

          os.owner.set(wd / "File.txt", "nobody")
          os.owner(wd / "File.txt").getName ==> "nobody"

          os.owner.set(wd / "File.txt", originalOwner)
        }
      }}
    }
    test("group"){
      test - prep { wd => if (Unix()){
        // Only works as root :(
        if (false){
          val originalOwner = os.owner(wd / "File.txt")

          os.owner.set(wd / "File.txt", "nobody")
          os.owner(wd / "File.txt").getName ==> "nobody"

          os.owner.set(wd / "File.txt", originalOwner)
        }
      }}
    }
  }
}
