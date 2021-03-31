

object main {
  def main(args: Array[String]) : Unit = {
    os.watch.watch(Seq(os.pwd), println, (k,v) => println(s"  $k $v"))
    Thread.sleep(1000000)
  }
}
