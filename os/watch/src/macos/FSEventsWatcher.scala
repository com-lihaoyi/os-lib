package os.watch.macos

import com.sun.jna.{NativeLong, Pointer}
import os.watch.Watcher

import scala.util.control.NonFatal

/**
 * Implements a watcher using FSEvents on macOS.
 */
class FSEventsWatcher(
    srcs: Seq[os.Path],
    onEvent: Set[os.Path] => Unit,
    filter: os.Path => Boolean,
    logger: (String, Any) => Unit,
    latency: Double
) extends Watcher {
  import CoreServicesApi.INSTANCE._
  import DispatchApi.INSTANCE._

  @volatile private var closed = false

  private val callback = new FSEventStreamCallback {
    def invoke(
        streamRef: FSEventStreamRef,
        clientCallBackInfo: Pointer,
        numEvents: NativeLong,
        eventPaths: Pointer,
        eventFlags: Pointer,
        eventIds: Pointer
    ): Unit = {
      try {
        val length = numEvents.intValue
        val pathStrings = eventPaths.getStringArray(0, length)
        logger("FSEVENT", pathStrings)
        val paths = pathStrings.iterator.map(os.Path(_)).filter(filter).toArray
        val nestedPaths = collection.mutable.Buffer.empty[os.Path]
        // When folders are moved, OS-X does not emit file events for all sub-paths
        // within the new folder, so we are forced to walk that folder and emit the
        // paths ourselves
        for (p <- paths) {
          if (os.isDir(p, followLinks = false)) {
            try os.walk.stream(p).foreach(p => if (filter(p)) nestedPaths.append(p))
            catch {
              case NonFatal(_) => /*do nothing*/
            }
          }
        }
//        logger("FSEVENT paths", paths.toVector)
//        logger("FSEVENT nested paths", nestedPaths.toVector)
        onEvent((paths.iterator ++ nestedPaths.iterator).toSet)
      }
      catch {
        case e: Throwable =>
          logger("FSEVENT CALLBACK ERROR", e)
      }
    }
  }

  /** Used to signal the watcher to stop. */
  private val signal: Object = new Object

  def run(): Unit = {
    assert(!closed, "Cannot `run()` a closed watcher.")

    def startFsEventStream[A](streamRef: FSEventStreamRef)(f: => A): A = {
      val success = FSEventStreamStart(streamRef)
      if (!success) throw new IllegalStateException("FSEventStreamStart returned false")

      try f
      finally FSEventStreamStop(streamRef)
    }

    val pathsToWatchCfStrings = NativeUtils.assertAllSome(
      srcs.iterator.map(path => CFStringRef(path.toString)).toArray,
      CFRelease
    ).getOrElse(throw new IllegalStateException(s"Can't create CFStrings for $srcs, some values were null"))
    try {
      val pathsToWatchArrayRef = CFArrayCreate(pathsToWatchCfStrings.map(_.getPointer)).getOrElse(
        throw new IllegalStateException(s"CFArrayCreate returned null for $srcs")
      )
      try {
        val streamRef = FSEventStreamCreate(
          callback,
          pathsToWatchArrayRef,
          sinceWhen = -1,
          latency,
          flags =
          // Flags defined at https://developer.apple.com/documentation/coreservices/1455376-fseventstreamcreateflags?language=objc
          //
          // File-level notifications https://developer.apple.com/documentation/coreservices/1455376-fseventstreamcreateflags/kfseventstreamcreateflagfileevents?language=objc
            0x00000010 |
              // Don't defer https://developer.apple.com/documentation/coreservices/1455376-fseventstreamcreateflags/kfseventstreamcreateflagnodefer?language=objc
              0x00000002
        )
        try {
          val queue = dispatch_queue_create("os.watch.macos.FSEventsWatcher")
          try {
            FSEventStreamSetDispatchQueue(streamRef, queue)
            try {
              startFsEventStream(streamRef) {
                // We don't really need to have this thread blocked, as macOS has its own dispatch threads,
                // but because Linux/Windows watchers are blocking, we do the same here in the sake of
                // simplicity.
                signal.synchronized(signal.wait())
              }
            }
            finally FSEventStreamInvalidate(streamRef)
          }
          finally dispatch_release(queue)
        }
        finally FSEventStreamRelease(streamRef)
      }
      finally CFRelease(pathsToWatchArrayRef)
    }
    finally pathsToWatchCfStrings.foreach(CFRelease)
  }

  /** This is thread-safe and idempotent. */
  def close(): Unit = {
    signal.synchronized(signal.notify())
    closed = true
  }
}
