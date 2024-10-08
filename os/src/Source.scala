package os

import java.io.{
  ByteArrayInputStream,
  InputStream,
  OutputStream,
  SequenceInputStream,
  BufferedOutputStream
}
import java.nio.channels.{
  Channels,
  FileChannel,
  ReadableByteChannel,
  SeekableByteChannel,
  WritableByteChannel
}

import scala.language.implicitConversions

/**
 * A source of bytes; must provide either an [[InputStream]] or a
 * [[SeekableByteChannel]] to read from. Can be constructed implicitly from
 * strings, byte arrays, inputstreams, channels or file paths
 */
trait Source extends geny.Writable {
  override def httpContentType = Some("application/octet-stream")
  def getHandle(): Either[geny.Writable, SeekableByteChannel]
  def writeBytesTo(out: java.io.OutputStream) = getHandle() match {
    case Left(bs) => bs.writeBytesTo(out)

    case Right(channel: FileChannel) =>
      val outChannel = Channels.newChannel(out)
      channel.transferTo(0, Long.MaxValue, outChannel)

    case Right(channel) =>
      val inChannel = Channels.newInputStream(channel)
      Internals.transfer(inChannel, out)
  }
  def writeBytesTo(out: WritableByteChannel) = getHandle() match {
    case Left(bs) =>
      val os = new BufferedOutputStream(Channels.newOutputStream(out))
      bs.writeBytesTo(os)
      os.flush()
    case Right(channel) =>
      (channel, out) match {
        case (src: FileChannel, dest) =>
          val size = src.size()
          var pos = 0L
          while (pos < size) {
            pos += src.transferTo(pos, size - pos, dest)
          }
        case (src, dest: FileChannel) => dest.transferFrom(src, 0, Long.MaxValue)
        case (src, dest) =>
          val os = new BufferedOutputStream(Channels.newOutputStream(dest))
          Internals.transfer(Channels.newInputStream(src), os)
          os.flush()
      }

  }
}

object Source extends WritableLowPri {
  implicit class ChannelSource(cn: SeekableByteChannel) extends Source {
    def getHandle() = Right(cn)
  }

  implicit class WritableSource[T](s: T)(implicit f: T => geny.Writable) extends Source {
    val writable = f(s)
    def getHandle() = Left(writable)
  }
}

trait WritableLowPri {
  implicit def WritableGenerator[T](a: geny.Generator[T])(implicit
      f: T => geny.Writable
  ): Source = {
    val f0 = f
    new Source {
      def getHandle() = Left(
        new geny.Writable {
          def writeBytesTo(out: java.io.OutputStream) = {
            for (x <- a) f0(x).writeBytesTo(out)
          }
        }
      )
    }
  }
  implicit def WritableTraversable[M[_], T](a: M[T])(implicit
      f: T => geny.Writable,
      g: M[T] => TraversableOnce[T]
  ): Source = {
    val traversable = g(a)
    val f0 = f
    new Source {
      def getHandle() = Left(
        new geny.Writable {
          def writeBytesTo(out: java.io.OutputStream) = {
            for (x <- traversable) f0(x).writeBytesTo(out)
          }
        }
      )
    }
  }
}

/**
 * A source which is guaranteeds to provide a [[SeekableByteChannel]]
 */
trait SeekableSource extends Source {
  def getHandle(): Right[geny.Writable, SeekableByteChannel]
  def getChannel() = getHandle().right.get
}

object SeekableSource {
  implicit class ChannelSource(cn: SeekableByteChannel) extends SeekableSource {
    def getHandle() = Right(cn)
  }
  class ChannelLengthSource(cn: SeekableByteChannel, length: Long) extends SeekableSource {
    def getHandle() = Right(cn)

    override def contentLength: Option[Long] = Some(length)
  }
}
