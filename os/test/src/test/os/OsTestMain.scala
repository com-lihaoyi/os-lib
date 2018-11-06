package test.os

object OsTestMain {
  def main(args: Array[String]): Unit = {
    val tar = os.proc("tar", "cvf", "-", ".").spawn(stderr = os.Inherit)
    val hash = os.proc("md5").spawn(stdin = tar.stdout, stderr = os.Inherit)
    println(hash.stdout.readString())
  }
}
