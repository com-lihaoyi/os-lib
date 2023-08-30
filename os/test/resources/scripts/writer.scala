import sun.misc.Signal


object Writer {
  def main(args: Array[String]): Unit = {
    Signal.handle(new Signal("PIPE"), (_: Signal) => {
      System.err.println("Got PIPE - exiting")
      System.exit(0)
    })

    val writeN = args(0).toInt
    val writeSleep = args(1).toInt
    var i = 0
    while(writeN == - 1 || i <= writeN) {
      println("Hello " + i)
      System.err.println("Written " + i)
      Thread.sleep(writeSleep)
      i += 1
    }
  }
}