import scala.io.StdIn.readLine

object Main {
  def main(args: Array[String]): Unit = {
    val readN = args(0).toInt
    val readSleep = args(1).toInt
    var i = 0
    while(i < readN) {
      System.err.println("At: " + i)
      val read = readLine()
      println("Read: " + read)
      Thread.sleep(readSleep)
      i += 1
    }
  }
}
