package os

import os.PathChunk.segmentsFromString
import utest.{assert => _, _}

object SegmentsFromStringTests extends TestSuite {

  val tests = Tests {
    test("segmentsFromString") {
      def testSegmentsFromString(s: String, expected: List[String]) = {
        assert(segmentsFromString(s).sameElements(expected))
      }

      testSegmentsFromString("  ", List("  "))

      testSegmentsFromString("", List(""))

      testSegmentsFromString("""foo/bar/baz""", List("foo", "bar", "baz"))

      testSegmentsFromString("""/""", List("", ""))
      testSegmentsFromString("""//""", List("", "", ""))
      testSegmentsFromString("""///""", List("", "", "", ""))

      testSegmentsFromString("""a/""", List("a", ""))
      testSegmentsFromString("""a//""", List("a", "", ""))
      testSegmentsFromString("""a///""", List("a", "", "", ""))

      testSegmentsFromString("""ahs/""", List("ahs", ""))
      testSegmentsFromString("""ahs//""", List("ahs", "", ""))

      testSegmentsFromString("""ahs/aa/""", List("ahs", "aa", ""))
      testSegmentsFromString("""ahs/aa//""", List("ahs", "aa", "", ""))

      testSegmentsFromString("""/a""", List("", "a"))
      testSegmentsFromString("""//a""", List("", "", "a"))
      testSegmentsFromString("""//a/""", List("", "", "a", ""))
    }
  }
}
