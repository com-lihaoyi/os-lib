package test.os

import os._
import utest._
import com.eed3si9n.expecty.Expecty.expect

object ExpectyIntegration extends TestSuite {
  val tests = Tests {
    test("Literals") {
      test("Basic") {
        expect(rel / "src" / "Main/.scala" == rel / "src" / "Main" / ".scala")
        expect(root / "core/src/test" == root / "core" / "src" / "test")
        expect(root / "core/src/test" == root / "core" / "src/test")
      }
      test("literals with [..]") {
        expect(rel / "src" / ".." == rel / "src" / os.up)
        expect(root / "src" / ".." == root / "src" / os.up)
        expect(root / "hello" / ".." / "world" == root / "hello" / os.up / "world")
        expect(root / "hello" / "../world" == root / "hello" / os.up / "world")
      }
      test("from issue") {
        expect(Seq(os.pwd / "foo") == Seq(os.pwd / "foo"))
        val path = os.Path("/") / "tmp" / "foo"
        expect(path.startsWith(os.Path("/") / "tmp"))
      }
      test("multiple args") {
        expect(rel / "src" / ".." == rel / "src" / os.up, root / "src" / "../foo" == root / "src" / os.up / "foo")
      }
    }
  }
}
