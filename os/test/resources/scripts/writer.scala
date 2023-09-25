
object Writer {
  def isWindows = System.getProperty("os.name").toLowerCase().contains("windows")
  def main(args: Array[String]): Unit = {
    val writeN = args(0).toInt
    val writeSleep = args(1).toInt
    val debugOutput = args(2).toBoolean
    var i = 0
    while(writeN == -1 || i < writeN) {
      println("Hello " + i)
      if(debugOutput) System.err.println("Written " + i)
      Thread.sleep(writeSleep)
      i += 1
    }

    if(debugOutput) System.err.println("Exiting writer")
  }
}