package os

import java.io.{InputStream, OutputStream}

import geny.Generator

import scala.io.Codec


/**
  * A path that can be read from, either a [[Path]] or a [[ResourcePath]].
  * Encapsulates the logic of how to read from it in various ways.
  */
trait Readable{
  protected[os] def getInputStream(): java.io.InputStream
  protected[os] def getBytes(): Array[Byte] = {
    val is = getInputStream
    val out = new java.io.ByteArrayOutputStream()
    val buffer = new Array[Byte](4096)
    var r = 0
    while (r != -1) {
      r = is.read(buffer)
      if (r != -1) out.write(buffer, 0, r)
    }
    is.close()
    out.toByteArray
  }
  protected[os] def getLineIterator(charSet: Codec) = geny.Generator.selfClosing{
    val is = getInputStream
    val s = scala.io.Source.fromInputStream(is)(charSet)
    (s.getLines(), () => s.close())
  }

  protected[os] def getLines(charSet: Codec): IndexedSeq[String] = {
    getLineIterator(charSet).toArray[String]
  }
}

object Readable{
  implicit class InputStreamToReadable(is: InputStream) extends Readable{
    def getInputStream() = is
  }
}

trait Writable{
  def write(out: OutputStream): Unit
}

object Writable extends WritableLowPri {
  implicit def WritableString(s: String) = new Writable{
    def write(out: OutputStream): Unit = out.write(s.getBytes)
  }
  implicit def WritableBytes(a: Array[Byte]): Writable = new Writable {
    def write(out: OutputStream): Unit = out.write(a)
  }
  implicit def WritableInputStream(is: InputStream): Writable = new Writable{
    def write(out: OutputStream): Unit = {
      val out = new java.io.ByteArrayOutputStream()
      val buffer = new Array[Byte](4096)
      var r = 0
      while (r != -1) {
        r = is.read(buffer)
        if (r != -1) out.write(buffer, 0, r)
      }
      is.close()
      out.toByteArray
    }
  }
}
trait WritableLowPri {
  implicit def WritableGenerator[M[_], T](a: M[T])
                                         (implicit f: T => Writable,
                                          i: M[T] => geny.Generator[T]) = {
    new Writable {
      def write(out: OutputStream): Unit = {
        i(a).foreach(f(_).write(out))
      }
    }
  }
}