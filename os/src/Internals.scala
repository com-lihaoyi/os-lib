package os

import scala.util.control.Breaks._

import java.io.{InputStream, OutputStream}
import java.nio.file.Files

object Internals{

  val emptyStringArray = Array.empty[String]

  def transfer0(src: InputStream,
                sink: (Array[Byte], Int) => Unit) = {
    val buffer = new Array[Byte](8192)
    breakable {
      while (true) {
        val r = src.read(buffer)
        if (r == -1) break
        sink(buffer, r)
      }
    }
    src.close()
  }

  def transfer(src: InputStream, dest: OutputStream) = transfer0(
    src,
    dest.write(_, 0, _)
  )
}
