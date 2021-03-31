package os.watch.inotify

import com.sun.jna.Structure

class PollFd(var fd: Int = 0, var events: Short = 0, var revents: Short = 0)  {
  
}
