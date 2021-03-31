package os.watch.inotify

import com.sun.jna.{LastErrorException, Library, Native}
import geny.Generator

import java.nio.{ByteBuffer, ByteOrder}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.Try

trait Notify extends Library {
  //@throws[LastErrorException]
  def inotify_init() : Int;
  
  //@throws[LastErrorException]
  def inotify_init1(flags: Int) : Int

  //@throws[LastErrorException]
  def inotify_add_watch(fd: Int, path: String, mask: Int): Int

  //@throws[LastErrorException]
  def inotify_rm_watch(fd: Int, wd: Int): Int
  
  def poll(fds: Array[Byte], nfds: Int, timeout: Int): Int

  //@throws[LastErrorException]
  def read(fd: Int, buf: Array[Byte], count: Long): Long

  //@throws[LastErrorException]
  def close(fd: Int): Int
}

object Notify {
  val it : Notify = Native.load("c",classOf[Notify])
  
  import Generator._

  // convenience
  def add_watch(fd: Int, path : os.Path, actions: Mask) : Int = {
    it.inotify_add_watch(fd,path.toString,actions.mask)
  }
  
  def poll_read(fd: Int, timeout: Int): Boolean = {
    val data = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
    data.putInt(fd)
    data.putShort(Constants.POLLIN)
    data.putShort(0)
    val rc = it.poll(data.array(),1,timeout)
    if (rc == 0) {
      false
    } else if (rc == 1) {
      data.rewind()
      //println(s"fd : ${data.getInt()} $fd")
      //println(f"events : ${data.getShort}%04x")
      //val revent = data.getShort 
      //println(f"revent: ${revent}%04x")
      return (data.getShort(6) & Constants.POLLIN) != 0
      true
    } else {
      throw new NotifyException(s"poll error, fd:$fd, errno:${Native.getLastError}")
    }
  }
  
  // Event processing

  def events(buf: ByteBuffer): Generator[Event] = new Generator[Event]() {
    override def generate(handleItem: Event => Action): Action = {
      while (buf.hasRemaining) {
        if (handleItem(Event(buf)) == End) return End
      }
      Continue
    }
  }

  def buffers(fd: AtomicReference[Option[Int]]): Generator[ByteBuffer] = {
    new Generator[ByteBuffer] {
      override def generate(handleItem: ByteBuffer => Action): Action = {

        val buffer = Array.fill[Byte](4096)(0)

        while (true) {
          fd.get match {
            case Some(fd) =>
              it.read(fd, buffer, buffer.length) match {
                case 0 =>
                  return End
                case n if n < 0 =>
                  val errno = Native.getLastError()
                  if (errno == Constants.EAGAIN) {
                    Thread.sleep(10)
                  } else {
                    throw new NotifyException(s"read error ${Native.getLastError()}, fd = $fd")
                  }
                case n =>
                  val buf = ByteBuffer.wrap(buffer, 0, n.toInt).order(ByteOrder.nativeOrder())
                  if (handleItem(buf) == End) {
                    return End
                  }
              }

            case None =>
              return End
          }
        }
        End
      }
    }
  }

  //
  // Produces a stream of events. Terminates when either
  //    - The 'fd' bcomes None
  //    - a handler returns End
  //    - a read error occurs
  //
  def events(fd: AtomicReference[Option[Int]]): Generator[Event] = for {
    b <- buffers(fd)
    e <- events(b)
  } yield e
}

