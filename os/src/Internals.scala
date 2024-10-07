package os

import java.io.{InputStream, OutputStream}
import java.nio.file.Files

object Internals {

  val emptyStringArray = Array.empty[String]

  def transfer0(src: InputStream, sink: (Array[Byte], Int) => Unit) = {
    transfer0(src, sink, true)
  }
  def transfer0(src: InputStream, sink: (Array[Byte], Int) => Unit, close: Boolean = true) = {
    val buffer = new Array[Byte](8192)
    var r = 0
    while (r != -1) {
      r = src.read(buffer)
      if (r != -1) sink(buffer, r)
    }
    if (close)src.close()
  }

  def transfer(src: InputStream, dest: OutputStream) = transfer(src, dest, true)
  def transfer(src: InputStream, dest: OutputStream, close: Boolean = true) = transfer0(
    src,
    dest.write(_, 0, _),
    close
  )
}
