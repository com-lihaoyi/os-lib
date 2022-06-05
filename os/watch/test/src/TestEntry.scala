package os.watch

import org.scalacheck._
//import upickle.default._

sealed trait TestEntry {
  //def name : String
  def make(root: os.Path, index: Int) : Seq[os.Path]
}

case object TestFile extends TestEntry {
  def make(root: os.Path, index: Int): Seq[os.Path] = {
    val p = root / f"f$index%04d"
    os.write(p , index.toString)
    Seq(p)
  }
}

case class TestDir(n_files: Int, dirs: Seq[TestEntry]) extends TestEntry {
  def make(root: os.Path, index: Int): Seq[os.Path] = {
    val me = root / f"d$index%04d"
    os.makeDir(me)
    
    val child_files = (0 to n_files).flatMap(i => TestFile.make(me,i))
    
    val child_dirs = dirs.zipWithIndex.flatMap { case(child,index) =>
      child.make(me,index)
    }
    
    Seq(me) ++ child_files ++ child_dirs
  }
}

object TestDir {

  //implicit val rw : ReadWriter[TestDir] = macroRW

  def gen(limit: Int) : Gen[TestDir] =
    for {
      n_files <- Gen.choose(0,limit/3)
      n_dirs <- Gen.choose(0, 0.max(limit/3 - n_files - 1))
      n_left = 0.max(limit - n_files - n_dirs)
      dirs <- Gen.listOfN(n_dirs,Gen.delay(TestDir.gen(n_left/n_dirs+1)))
    } yield TestDir(n_files,dirs)
    
  //implicit def shrink : Shrink[TestDir] = Shrink { (dir:TestDir) =>
  //  dir.children.tails.map(t => TestDir(dir.name,t)).toStream
  //}
  
}
