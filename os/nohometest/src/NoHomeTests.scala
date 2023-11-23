package test.os

import utest._

object NoHomeTests extends TestSuite {
  val tests = Tests {
    test("pwd when home is not available") {
      System.setProperty("user.home", "?")
      val homeException =
        intercept[ExceptionInInitializerError] { os.home }
          .getCause()
          .getMessage()
      assert(homeException == "requirement failed: ? is not an absolute path")
      os.pwd
      ()
    }
  }
}
