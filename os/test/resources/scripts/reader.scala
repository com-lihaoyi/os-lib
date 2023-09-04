import scala.io.StdIn.readLine

object Main {
  def main(args: Array[String]): Unit = {
    val readN = args(0).toInt
    val readSleep = args(1).toInt
    val debugOutput = args(2).toBoolean
    var i = 0
    while(readN == -1 || i < readN) {
      if(debugOutput) System.err.println("At: " + i)
      val read = readLine()
      println("Read: " + read)
      Thread.sleep(readSleep)
      i += 1
    }
    if(debugOutput) System.err.println("Exiting reader")
  }
}
