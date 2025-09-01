package test.os

import utest._

/**
 * Placeholder for Native: real ZipOp tests live under test/src-jvm.
 * This avoids referencing JVM-only os.zip/unzip APIs on Scala Native.
 */
object ZipOpNativePlaceholderTests extends TestSuite {
  def tests = Tests {
    test("zipOps are JVM-only") {
      assert(true)
    }
  }
}
