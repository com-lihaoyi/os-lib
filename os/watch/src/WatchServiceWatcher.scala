package os.watch

import java.nio.file._
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.StandardWatchEventKinds.{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW}

import com.sun.nio.file.SensitivityWatchEventModifier

import scala.collection.mutable
import collection.JavaConverters._

class WatchServiceWatcher(roots: Seq[os.Path],
                          onEvent: Set[os.Path] => Unit,
                          logger: (String, Any) => Unit = (_, _) => ()) extends Watcher{

  val nioWatchService = FileSystems.getDefault.newWatchService()
  val currentlyWatchedPaths = mutable.Map.empty[os.Path, WatchKey]
  val newlyWatchedPaths = mutable.Buffer.empty[os.Path]
  val bufferedEvents = mutable.Set.empty[os.Path]
  val isRunning = new AtomicBoolean(false)

  isRunning.set(true)

  roots.foreach(watchSinglePath)
  recursiveWatches()

  def watchSinglePath(p: os.Path) = {
    val isDir = os.isDir(p, followLinks = false)
    logger("WATCH", (p, isDir))
    if (isDir) {
      currentlyWatchedPaths.put(
        p,
        p.toNIO.register(
          nioWatchService,
          Array[WatchEvent.Kind[_]](ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW),
          SensitivityWatchEventModifier.HIGH
        )
      )
      newlyWatchedPaths.append(p)
    }
    bufferedEvents.add(p)
  }

  def processEvent(watchKey: WatchKey) = {
    val p = os.Path(watchKey.watchable().asInstanceOf[java.nio.file.Path], os.pwd)
    logger("WATCH PATH", p)

    val events = watchKey.pollEvents().asScala

    logger("WATCH CONTEXTS", events.map(_.context()))

    logger("WATCH KINDS", events.map(_.kind()))

    for(e <- events){
      bufferedEvents.add(p / e.context().toString)
    }

    for(e <- events if e.kind() == ENTRY_CREATE){
      watchSinglePath(p / e.context().toString)
    }

    watchKey.reset()
  }

  def recursiveWatches() = {
    while(newlyWatchedPaths.nonEmpty){
      val top = newlyWatchedPaths.remove(newlyWatchedPaths.length - 1)
      val listing = try os.list(top) catch {case e: java.nio.file.NotDirectoryException => Nil }
      for(p <- listing) watchSinglePath(p)
      bufferedEvents.add(top)
    }
  }

  def start(): Unit = {
    while (isRunning.get()) try {
      logger("WATCH CURRENT", currentlyWatchedPaths)
      val watchKey0 = nioWatchService.take()
      if (watchKey0 != null){
        logger("WATCH KEY0", watchKey0.watchable())
        processEvent(watchKey0)
        while({
          nioWatchService.poll() match{
            case null => false
            case watchKey =>
              logger("WATCH KEY", watchKey.watchable())
              processEvent(watchKey)
              true
          }
        })()

        recursiveWatches()
        triggerListener()

        // cleanup stale watches
        for(p <- currentlyWatchedPaths.keySet if !os.isDir(p, followLinks = false)){
          logger("WATCH CANCEL", p)
          currentlyWatchedPaths.remove(p).foreach(_.cancel())
        }
      }

    } catch {
      case e: InterruptedException =>
        println("Interrupted, exiting", e)
        isRunning.set(false)
      case e: ClosedWatchServiceException =>
        println("Watcher closed, exiting", e)
        isRunning.set(false)
    }
  }

  def close(): Unit = {
    try {
      isRunning.set(false)
      nioWatchService.close()
    } catch {
      case e: IOException => println("Error closing watcher", e)
    }
  }

  private def triggerListener(): Unit = {
    onEvent(bufferedEvents.toSet)
    bufferedEvents.clear()
  }
}