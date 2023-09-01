import scala.io.StdIn.readLine

object Main {
  def main(args: Array[String]): Unit = {
    val exitCode = args(0).toInt
    val exitSleep = args(1).toInt
    System.err.println("Exiting with code: " + exitCode)
    Thread.sleep(exitSleep)
    System.exit(exitCode)
  }
}
