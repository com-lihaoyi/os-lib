package os.watch.inotify

import com.sun.jna.{LastErrorException, Native}
import os.watch.Watcher

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.util.control.NonFatal

/*
     An inotify based file system watcher.
     
     Semantics:
     
         - Runs its own thread and reports events on that thread
         - Promises at-least one semantics
         - Reports events in the same order produced by 'inotify'
         - Callers track whole file system hierarchies. Maintains an
           in-memory represetatnion of the folders in the hierarchy
         - The underlying service (inotify) only supports monitoring
           folders so the impementation is self-serving; reacts to folder
           creation events by walking the new hierarchy and registering
           listeners.
           
       Attempts to be a drop-in replacement for the original WatchServiceWatcher
       that avoids some of the observed race conditions (mostly caused by
       WatchService reordering events).
       
       Some of the inconsistencies between Linux and OSX are carried over because
       if the need to keep the WatchServiceWatcher semantics. The implementation
       could be changed to match OSX if needed.
       
       Bugs:
       
           - Some stress tests fail (with a small probablity) when running in Docker.
             It is still doing better the the WatchServiceWatcher which fails quickly
             when run natively.
             
             This issue shouldn't be taken lightly and I will continue to investigate
             it until I get to the bottom of it
             
           - os.watch.watch behaves differently on OSX and Linux. Those differences
             where there before and preserved by the current implementation.
             
             Those inconsistencies could (and should) be eliminated.
             
           - Overflow events cause the event processing thread to print a stack
             trace and terminate.
             
        API issues:
        
           - os.watch.watch has no business creating its own thread. It should
             return a geny.Generator and let the caller decide how to process it
             
           - no mechanism for error reporting
           
           How about:
           
                def watch2(paths: Seq[os.Path]): geny.Generator[Either[Error,os.Path]]
 */

// The inotify API works with watch ids (wd).
// When we add a path to watch, inotify returns a 'wd'
// When we want to stop watching, we give it the 'wd'
// When it reports evenets, it gives us the 'wd'
//
// We use the structure below to manage the two-way mapping
class WatchedPaths {
  private val pathToId = mutable.Map[os.Path,Int]()
  private val idToPath = mutable.Map[Int,os.Path]()
  
  def put(p: os.Path, id: Int) = synchronized {
    pathToId.put(p,id)
    idToPath.put(id,p)
  }
  
  def get(path: os.Path) : Option[Int] = this.synchronized {
    pathToId.get(path)
  }
  
  def get(wd: Int) : Option[os.Path] = this.synchronized {
    idToPath.get(wd)
  }
  
  // unwatch the given path and all of its subpaths
  // TODO: can do better than iterating over the entire key space
  def remove(p: os.Path) : Seq[Int] = this.synchronized {
    pathToId.keys.filter(_.startsWith(p)).toSeq.flatMap { r =>
      val wd = pathToId.remove(r)
      wd.foreach(wd => idToPath.remove(wd))
      wd
    }
  }
}

class NotifyWatcher(roots: Seq[os.Path],
                          onEvent: Set[os.Path] => Unit,
                          logger: (String, Any) => Unit = (_, _) => ()) extends Watcher{

  val fd  = new AtomicReference(Option(Notify.it.inotify_init1(0))) //Constants.O_NONBLOCK)))
  if (fd.get.get < 0) {
    throw new NotifyException(s"inotify failed")
  }
  val currentlyWatchedPaths = new WatchedPaths()
  val newlyWatchedPaths = mutable.Buffer.empty[os.Path]
  val bufferedEvents = mutable.Set.empty[os.Path]
  
  val close_signal = os.temp.dir(deleteOnExit = true)
  val close_wd = Notify.add_watch(fd.get.get,close_signal,Mask.delete_self)

  roots.foreach(watchSinglePath)
  recursiveWatches()

  bufferedEvents.clear()
  
  def fatal(msg: String): Unit = {
    throw new NotifyException(msg)
  }
  
  def watchSinglePath(p: os.Path) = fd.get.foreach { fd =>
    // The system watches i-nodes
    val wd = Notify.add_watch(fd, p,
      Mask.create |       // mkdir a/b => Event("a", CREATE, "b")
        Mask.delete |     // rm a/b => Event("a", DELETE, "b")
        Mask.modify |     // touch a/b => Event("a", MODIFY, "b")
        Mask.move_from |  // mv a/b a/c => Event("a", MOVE_FROM, "b")
                          //               Event("a", MOVE_TO, "b")
        Mask.move_to |
        Mask.do_not_follow |
        Mask.only_dir |    // fail if p doesn't refer to a directory
                           // why not check before calling add_watch? race condition
        Mask.mask_add      // add it to existing watch if any
    )
    
    bufferedEvents.add(p)
    
    if (wd < 0) {
      //val err = Native.getLastError()
      // failing to watch is not always an error. For example:
      //     - not a directory
      //     - file is gone
      //     - permissions
      // TODO: are there cases that should be reported? How?
    } else {
      logger("WATCH", (p, wd))
      currentlyWatchedPaths.put(p, wd)  // a new watch path to track
      newlyWatchedPaths.append(p)       // add it to todo list
    }
  }

  def processEvent(event: Event) = {
    currentlyWatchedPaths.get(event.wd).foreach { base =>
      logger("BASE", base)

      val p = if (event.name == "") base else base / event.name
      
      // events that don't need to be reported
      
      // A directory we used to watch is gone
      val ignored = event.mask.contains(Mask.ignored)
      
      // The directory itself has been modified. Events that we care about
      // will produce two events:
      //    rm a/b =>
      //       EVENT("a", DELETE, "b")
      //       EVENT("a", MODIFY)
      val folder_update = event.mask.contains(Mask.is_dir | Mask.modify)
      
      // things we should report. We still process the event but we need
      // to decide if we need to report it back
      // TODO: those rules can be adjusted to match OSX semantics if needed
      val should_report = (!ignored) && (!folder_update)
      
      if (should_report) {
        bufferedEvents.add(p)
      }

      // See if we need to update (add/remove) watches
      if (event.mask.contains(Mask.create) || event.mask.contains(Mask.move_to)) {
        // A new thing materalized, start watching it
        // Why not ignore it if it's not a directory? race conditions
        watchSinglePath(p)
      } else if (event.mask.contains(Mask.delete) ||
        event.mask.contains(Mask.ignored) ||
        event.mask.contains(Mask.move_from)) {
        // Something disappered, stop watching it
        logger("UNWATCH PATH",p)
        currentlyWatchedPaths.remove(p).foreach { wd =>
          logger("UNWATCH WD",wd)
          fd.get.foreach { fd =>
            Notify.it.inotify_rm_watch(fd, wd)
          }
        }
      }
    }
  }

  def recursiveWatches() = {
    while(newlyWatchedPaths.nonEmpty){
      val top = newlyWatchedPaths.remove(newlyWatchedPaths.length - 1)
      val listing = try os.list(top) catch {case e: java.nio.file.NotDirectoryException => Nil }
      for(p <- listing) watchSinglePath(p)
      bufferedEvents.add(top)
    }
  }

  def run(): Unit = {
    try {
      Notify.events(fd).foreach { event =>
        logger("EVENT",event)
        
        if ((event.wd == close_wd) && (event.mask.contains(Mask.delete_self))) {
          close()
          throw new NotifyException("done")
        }
        
        if (event.mask.contains(Mask.overflow)) {
          // TODO: We can do better but it might be tricky
          // TODO: The least we can do is report the event back to the caller and let them react
          throw new Exception(s"scate events ${event.toString}")
        }
        
        // process the event
        processEvent(event)
        
        // add any resulting watches.
        // For example: mv a/b a/c # if be is a directory
        recursiveWatches()
        
        // report events back
        triggerListener()
      }
    } catch {
      case _ : InterruptedException =>
        close()
      case NonFatal(e) =>
        if (fd.get.isEmpty) {
          // close was called, ignore the error
        } else {
          // TODO: do better (restart, report to user, etc)
          e.printStackTrace()
          close()
        }
    }
  }

  def close(): Unit = {
    fd.get.foreach { it =>
      fd.set(None)
      Notify.it.close(it)
      os.remove.all(close_signal)
    }
  }

  private def triggerListener(): Unit = {
    val s = bufferedEvents.toSet
    if (fd.get.nonEmpty) {
      logger("TRIGGER", s)
      onEvent(s)
    }
    bufferedEvents.clear()
  }
}