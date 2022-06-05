package os.watch.inotify

import java.nio.ByteBuffer

case class Event(val wd: Int, val mask: Mask, val cookie: Int, val name: String) {



}

object Event {
  def apply(buf: ByteBuffer): Event = {
    val wd = buf.getInt
    val mask = buf.getInt
    val cookie = buf.getInt
    val len = buf.getInt
    val sb = new StringBuilder()
    
    var i = 0
    
    while (i < len) {
      val b = buf.get()
      if (b != 0) {
        sb.append(b.toChar)
      }
      i += 1
    }
    
    Event(wd, Mask(mask), cookie, sb.toString)
  }
}
