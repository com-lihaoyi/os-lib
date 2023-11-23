package test.os

import utest._

object NoHomeTests extends TestSuite {
  val tests = Tests {
    test("pwd when home is not available") {
      System.setProperty("user.home", "?")
      os.pwd
      ()
    }
  }
}
