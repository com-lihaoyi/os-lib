package os.watch

import java.nio.file._
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.StandardWatchEventKinds.{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW}
import com.sun.nio.file.{ExtendedWatchEventModifier, SensitivityWatchEventModifier}

import scala.collection.mutable
import collection.JavaConverters._
import scala.util.Properties.isWin

class WatchServiceWatcher(
    roots: Seq[os.Path],
    onEvent: Set[os.Path] => Unit,
    filter: os.Path => Boolean,
    logger: (String, Any) => Unit
) extends Watcher {
  import WatchServiceWatcher.WatchEventOps

  val nioWatchService = FileSystems.getDefault.newWatchService()
  val currentlyWatchedPaths = mutable.Map.empty[os.Path, WatchKey]
  val newlyWatchedPaths = mutable.Buffer.empty[os.Path]
  val bufferedEvents = mutable.Set.empty[os.Path]
  val isRunning = new AtomicBoolean(false)

  isRunning.set(true)

  roots.foreach(watchSinglePath)
  recursiveWatches()

  bufferedEvents.clear()
  def watchSinglePath(p: os.Path) = {
    val isDir = os.isDir(p, followLinks = false)
    logger("WATCH", (p, isDir))
    if (isDir) {
      // https://stackoverflow.com/a/6265860/4496364
      // on Windows we watch only the root directory
      val modifiers: Array[WatchEvent.Modifier] = if (isWin)
        Array(SensitivityWatchEventModifier.HIGH, ExtendedWatchEventModifier.FILE_TREE)
      else Array(SensitivityWatchEventModifier.HIGH)
      currentlyWatchedPaths.put(
        p,
        p.toNIO.register(
          nioWatchService,
          Array[WatchEvent.Kind[_]](ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW),
          modifiers: _*
        )
      )
      if (filter(p)) newlyWatchedPaths.append(p)
    }
    bufferedEvents.add(p)
  }

  def processEvent(watchKey: WatchKey) = {
    val p = os.Path(watchKey.watchable().asInstanceOf[java.nio.file.Path], os.pwd)
    logger("WATCH PATH", p)

    val events = watchKey.pollEvents().asScala

    logger("WATCH CONTEXTS", events.map(_.context()))

    logger("WATCH KINDS", events.map(_.kind()))

    def logWarning(msg: String): Unit = {
      System.err.println(s"[oslib.watch] (path=$p) $msg")
    }

    def logWarningContextNull(e: WatchEvent[_]): Unit = {
      logWarning(
        s"Context is null for event kind='${e.kind().name()}' of class ${e.kind().`type`().getName}, " +
          s"this should never happen."
      )
    }

    for (e <- events) {
      if (e.kind() == OVERFLOW) {
        logWarning("Overflow detected, some filesystem changes may not be registered.")
      } else {
        e.contextSafe match {
          case Some(ctx) => bufferedEvents.add(p / ctx.toString)
          case None => logWarningContextNull(e)
        }
      }
    }

    for (e <- events if e.kind() == ENTRY_CREATE) {
      e.contextSafe match {
        case Some(ctx) => watchSinglePath(p / ctx.toString)
        case None => logWarningContextNull(e)
      }
    }

    watchKey.reset()
  }

  def recursiveWatches() = {
    // no need to recursively watch each folder on windows
    // https://stackoverflow.com/a/64030685/4496364
    if (isWin) {
      // noop
    } else {
      while (newlyWatchedPaths.nonEmpty) {
        val top = newlyWatchedPaths.remove(newlyWatchedPaths.length - 1)
        val listing =
          try os.list(top)
          catch {
            case _: java.nio.file.NotDirectoryException | _: java.nio.file.NoSuchFileException => Nil
          }
        for (p <- listing) watchSinglePath(p)
        bufferedEvents.add(top)
      }
    }
  }

  def run(): Unit = {
    while (isRunning.get())
      try {
        logger("WATCH CURRENT", currentlyWatchedPaths)
        val watchKey0 = nioWatchService.take()
        if (watchKey0 != null) {
          logger("WATCH KEY0", watchKey0.watchable())
          processEvent(watchKey0)
          while ({
            nioWatchService.poll() match {
              case null => false
              case watchKey =>
                logger("WATCH KEY", watchKey.watchable())
                processEvent(watchKey)
                true
            }
          }) ()

          // cleanup stale watches, but do so before we register new ones
          // because when folders are moved, the old watch is moved as well
          // and we need to make sure we re-register the watch after disabling
          // it due to the old file path within the old folder no longer existing
          for (p <- currentlyWatchedPaths.keySet if !os.isDir(p, followLinks = false)) {
            logger("WATCH CANCEL", p)
            currentlyWatchedPaths.remove(p).foreach(_.cancel())
          }

          recursiveWatches()
          triggerListener()
        }

      } catch {
        case e: InterruptedException =>
          logger("Interrupted, exiting.", e)
          isRunning.set(false)
        case e: ClosedWatchServiceException =>
          logger("Watcher closed, exiting.", e)
          isRunning.set(false)
      }
  }

  def close(): Unit = {
    try {
      isRunning.set(false)
      nioWatchService.close()
    } catch {
      case e: IOException => logger("Error closing watcher.", e)
    }
  }

  private def triggerListener(): Unit = {
    logger("TRIGGER", bufferedEvents.toSet)
    onEvent(bufferedEvents.toSet)
    bufferedEvents.clear()
  }
}
object WatchServiceWatcher {
  private implicit class WatchEventOps[A](private val e: WatchEvent[A]) extends AnyVal {
    def contextSafe: Option[A] = Option(e.context())
  }
}
