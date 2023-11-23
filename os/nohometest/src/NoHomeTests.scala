package test.os

import utest._

object NoHomeTests extends TestSuite {
  private lazy val isWindows = sys.props("os.name").toLowerCase().contains("windows")

  val tests = Tests {
    test("pwd when home is not available") {
      System.setProperty("user.home", "?")
      val homeException = intercept[IllegalArgumentException] { os.home }
        .getMessage()

      val expectedException =
        if (isWindows)
          "Illegal char <?> at index 0: ?"
        else
          "requirement failed: ? is not an absolute path"

      assert(homeException == expectedException)
      os.pwd
      ()
    }
  }
}
