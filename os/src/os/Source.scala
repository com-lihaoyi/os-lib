package os

import java.io.{ByteArrayInputStream, InputStream, OutputStream, SequenceInputStream}
import java.nio.channels.{Channels, FileChannel, ReadableByteChannel, SeekableByteChannel}


/**
  * A source of bytes; must provide either an [[InputStream]] or a
  * [[SeekableByteChannel]] to read from. Can be constructed implicitly from
  * strings, byte arrays, inputstreams, channels or file paths
  */
trait Source{
  def getInputStream(): java.io.InputStream = getHandle match{
    case Left(is) => is
    case Right(bc) => Channels.newInputStream(bc)
  }
  def getHandle(): Either[java.io.InputStream, SeekableByteChannel]
}

object Source extends WritableLowPri{
  implicit class ChannelSource(cn: SeekableByteChannel) extends Source{
    def getHandle() = Right(cn)
  }
  implicit class InputStreamSource(is: InputStream) extends Source{
    def getHandle() = Left(is)
  }

  implicit def StringSource(s: String) = new Source{
    def getHandle() = Left(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }
  implicit def BytesSource(a: Array[Byte]): Source = new Source{
    def getHandle() = Left(new ByteArrayInputStream(a))
  }
}
trait WritableLowPri {
  implicit def WritableGenerator[M[_], T](a: M[T])
                                         (implicit f: T => Source,
                                          g: M[T] => TraversableOnce[T]) = {
    new Source {
      def getHandle() = Left{
        import collection.JavaConverters._
        new SequenceInputStream(
          g(a).map(i => (f(i).getInputStream())).toIterator.asJavaEnumeration
        )
      }
    }
  }
}

/**
  * A source which is guaranteeds to provide a [[SeekableByteChannel]]
  */
trait SeekableSource extends Source{
  def getHandle(): Right[java.io.InputStream, SeekableByteChannel]
  def getChannel() = getHandle.right.get
}

object SeekableSource{
  implicit class ChannelSource(cn: SeekableByteChannel) extends Source{
    def getHandle() = Right(cn)
  }
}