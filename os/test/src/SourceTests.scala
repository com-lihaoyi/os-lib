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

      // Make sure the `Writable` returned by `os.read.stream` propagates the content length
      os.read.stream(wd / "folder1/one.txt").contentLength ==> Some(22)
      // Even when converted to an `os.Source`
      (os.read.stream(wd / "folder1/one.txt"): os.Source).contentLength ==> Some(22)
    }
  }
}
