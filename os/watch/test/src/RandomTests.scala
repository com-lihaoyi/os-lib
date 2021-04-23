package os.watch

import org.scalacheck.Gen.Parameters

import java.util.concurrent.atomic.AtomicInteger
import org.scalacheck.Prop.forAll
import org.scalacheck.Properties

import scala.util.control.NonFatal
import scala.util.Random
import scala.collection.mutable

import utest._
object RandomTests extends TestSuite {
  val tests = Tests {
    test("the_tests") - TheTests.check()
  }
}

object TheTests extends Properties("os.watch") {

  val base_dir : os.Path = os.pwd / "random_data"
  os.remove.all(base_dir)
  os.makeDir(base_dir)

  def show[A : Ordering](what: String, in: Iterable[A]): Unit = {
    println(what)
    in.toList.sorted.foreach { p =>
      println(s"    $p")
    }
  }
  
  val run_counter = new AtomicInteger(0)
  
  case class Comparison(expected: Set[os.Path], run: Run) {
    def compare(): Boolean = {
      val actual = run.actual(_.toSet)
      val id = run.id
      if (expected != actual) {
        val ok_file = base_dir / f"$id%04d.expected"
        val out_file = base_dir / f"$id%04d.actual"
        System.err.println(s"look for differences in $ok_file and $out_file")
        os.write(ok_file,expected.toList.sorted.mkString("\n"))
        os.write(out_file,actual.toList.sorted.mkString("\n"))
        println(":::: expected ::::")
        expected.toList.sorted.foreach(println)
        println(":::: actual ::::")
        actual.toList.sorted.foreach(println)
        println(":::: expected - actual ::::")
        (expected -- actual).toList.sorted.foreach(println)
        println(":::: actual - expected ::::")
        (actual -- expected).toList.sorted.foreach(println)
        println("::: events :::")
        run.events.foreach(println)
      }

      expected == actual
    }
  }
  
  val comparisons = mutable.Buffer[Comparison]()
  
  class Run() {
    val actual = Locked(mutable.Set[os.Path]())
    val events = mutable.Buffer[(String,Any)]()
    val id = run_counter.getAndIncrement()
    val dir = base_dir / f"run_$id%04d"
    os.makeDir(dir)
    
    private var watch : AutoCloseable = null
    
    def observe[A](f: => A): A = {
      watch = os.watch.watch(Seq(dir),
        { paths => paths.foreach(p => actual(_.add(p))) },
        { (k,v) => events.appendAll((Seq((k,v)))) },
        WatchConfig(preferNative = true))
      f
    }
    
    def check(expected: Set[os.Path]): Boolean = {
      var i = 0

      while ((expected.size > actual(_.size)) && (i < 30000)) {
        Thread.sleep(1)
        i += 1
      }
      
      watch.close()
      
      val c = Comparison(expected,this)

      val out = c.compare()
      if (out) {
        comparisons.appendAll(Seq(c))
      }
      out
    }
  }
  
  if (System.getProperty("os.name") == "Linux") {
    property("create") = forAll(TestDir.gen(50)) { d =>
      val run = new Run()
      //println(s"create#${run.id}")
      val made = run.observe {
        d.make(run.dir, 0)
      }
      run.check(made.toSet)
    } && {
      val out = comparisons.forall(_.compare())
      comparisons.clear()
      out
    }

    property("rm") = forAll(TestDir.gen(50)) { d =>
      val run = new Run()
      //println(s"rm#${run.id}")
      val expected = d.make(run.dir, 0).toSet
      run.observe {
        os.remove.all(run.dir)
      }
      run.check(expected)
    } && {
      val out = comparisons.forall(_.compare())
      comparisons.clear()
      out
    }

    property("update") = forAll(TestDir.gen(50)) { d =>
      val run = new Run()
      //println(s"update#${run.id}")
      val files = d.make(run.dir, 0).filter(p => os.isFile(p))
      val shuffled = Random.shuffle(files)
      run.observe {
        shuffled.foreach { p => os.write.over(p, "") }
      }
      run.check(files.toSet)
    } && {
      val out = comparisons.forall(_.compare())
      comparisons.clear()
      out
    }

    property("move_files") = forAll(TestDir.gen(50)) { d =>
      val run = new Run()
      //println(s"move_files#${run.id}")

      val things = d.make(run.dir, 0)
      val files = things.filter(p => os.isFile(p))

      val targets = run.observe {
        var i = 0
        files.map { p =>
          val target = run.dir / s"x$i"
          i += 1
          os.move(p, target)
          target
        }
      }
      run.check(files.toSet ++ targets.toSet)
    } && {
      val out = comparisons.forall(_.compare())
      comparisons.clear()
      out
    }
    
    property("move_folder") = forAll(TestDir.gen(50)) { d =>
      val run = new Run()
      //println(s"move_folder#${run.id}")
      
      val things = d.make(run.dir,0)
      
      val sources = os.list(run.dir).toList
      val target = run.dir / "it"
      os.makeDir(target)
      
      run.observe {
        sources.foreach { p =>
          os.move.into(p,target)
        }
      }
      
      val new_paths = things.map(_.relativeTo(run.dir)).map(target / _)
      
      run.check(new_paths.toSet ++ sources.toSet)
    } && {
      val out = comparisons.forall(_.compare())
      comparisons.clear()
      out
    }
  }

}
