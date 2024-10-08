package test.os
import utest.{assert => _, _}

object SourceTests extends TestSuite {

  val tests = Tests {
    test("contentMetadata") - TestUtil.prep { wd =>
      // content type for all files is just treated as application/octet-stream,
      // we do not do any clever mime-type inference or guessing
      (wd / "folder1/one.txt").toSource.httpContentType ==> Some("application/octet-stream")
      // length is taken from the filesystem at the moment at which `.toSource` is called
      (wd / "folder1/one.txt").toSource.contentLength ==> Some(22)
      (wd / "File.txt").toSource.contentLength ==> Some(8)
    }
  }
}
