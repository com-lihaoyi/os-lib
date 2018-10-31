package os

import java.io.{ByteArrayInputStream, InputStream, OutputStream, SequenceInputStream}


/**
  * A path that can be read from, either a [[Path]] or a [[ResourcePath]].
  * Encapsulates the logic of how to read from it in various ways.
  */
trait Source{
  def getInputStream(): java.io.InputStream
}

object Source extends WritableLowPri{
  implicit class InputStreamSource(is: InputStream) extends Source{
    def getInputStream() = is
  }

  implicit def StringSource(s: String) = new Source{
    def getInputStream() = new ByteArrayInputStream(s.getBytes())
  }
  implicit def BytesSource(a: Array[Byte]): Source = new Source{
    def getInputStream() = new ByteArrayInputStream(a)
  }
}
trait WritableLowPri {
  implicit def WritableGenerator[M[_], T](a: M[T])
                                         (implicit f: T => Source,
                                          g: M[T] => TraversableOnce[T]) = {
    new Source {
      def getInputStream() = {
        import collection.JavaConverters._


        new SequenceInputStream(
          g(a).map(i => (f(i).getInputStream())).toIterator.asJavaEnumeration
        )
      }
    }
  }
}
