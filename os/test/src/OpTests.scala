package test.os

import java.nio.file.NoSuchFileException
import java.nio.{file => nio}


import utest._
import os.{GlobSyntax, /}
object OpTests extends TestSuite{

  val tests = Tests {
    val res = os.pwd/'os/'test/'resources/'test
    test("ls") - assert(
      os.list(res).toSet == Set(
        res/'folder1,
        res/'folder2,
        res/'misc,
        res/'os,
        res/"File.txt",
        res/"Multi Line.txt"
      ),
      os.list(res/'folder2).toSet == Set(
        res/'folder2/'nestedA,
        res/'folder2/'nestedB
      )
    )
    test("rm"){
      // shouldn't crash
      os.remove.all(os.pwd/'out/'scratch/'nonexistent)
      // should crash
      intercept[NoSuchFileException]{
        os.remove(os.pwd/'out/'scratch/'nonexistent)
      }
    }
  }
}
