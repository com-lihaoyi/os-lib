package os

import java.io.{ByteArrayInputStream, InputStream, OutputStream, SequenceInputStream}
import java.nio.channels.{Channels, FileChannel, ReadableByteChannel, SeekableByteChannel}


/**
  * A path that can be read from, either a [[Path]] or a [[ResourcePath]].
  * Encapsulates the logic of how to read from it in various ways.
  */
trait Source{
  def getInputStream(): java.io.InputStream
  def getChannel(): Option[SeekableByteChannel]
}

object Source extends WritableLowPri{
  implicit class ChannelSource(cn: SeekableByteChannel) extends Source{
    def getInputStream() = Channels.newInputStream(cn)
    override def getChannel() = Some(cn)
  }
  implicit class InputStreamSource(is: InputStream) extends Source{
    def getInputStream() = is
    def getChannel() = None
  }

  implicit def StringSource(s: String) = new Source{
    def getInputStream() = new ByteArrayInputStream(s.getBytes())
    def getChannel() = None
  }
  implicit def BytesSource(a: Array[Byte]): Source = new Source{
    def getInputStream() = new ByteArrayInputStream(a)
    def getChannel() = None
  }
}
trait WritableLowPri {
  implicit def WritableGenerator[M[_], T](a: M[T])
                                         (implicit f: T => Source,
                                          g: M[T] => TraversableOnce[T]) = {
    new Source {
      def getChannel() = None
      def getInputStream() = {
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
  def getInputStream(): java.io.InputStream
  override def getChannel(): Some[SeekableByteChannel]
}

