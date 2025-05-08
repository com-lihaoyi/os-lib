package os.watch

import com.sun.jna.{NativeLong, Pointer}

import scala.util.control.NonFatal

class FSEventsWatcher(
    srcs: Seq[os.Path],
    onEvent: Set[os.Path] => Unit,
    filter: os.Path => Boolean,
    logger: (String, Any) => Unit,
    latency: Double
) extends Watcher {
  private[this] var closed = false
  private[this] val callback = new FSEventStreamCallback {
    def invoke(
        streamRef: FSEventStreamRef,
        clientCallBackInfo: Pointer,
        numEvents: NativeLong,
        eventPaths: Pointer,
        eventFlags: Pointer,
        eventIds: Pointer
    ) = {
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
      onEvent((paths.iterator ++ nestedPaths.iterator).toSet)
    }
  }

  private[this] val streamRef = CarbonApi.INSTANCE.FSEventStreamCreate(
    Pointer.NULL,
    callback,
    Pointer.NULL,
    CarbonApi.INSTANCE.CFArrayCreate(
      null,
      srcs.map(p => CFStringRef.toCFString(p.toString).getPointer).toArray,
      CFIndex.valueOf(srcs.length),
      null
    ),
    -1,
    latency,
    // Flags defined at https://developer.apple.com/documentation/coreservices
    // /1455376-fseventstreamcreateflags?language=objc
    //
    // File-level notifications https://developer.apple.com/documentation/coreservices
    // /1455376-fseventstreamcreateflags/kfseventstreamcreateflagfileevents?language=objc
    0x00000010 |
      //
      // Don't defer https://developer.apple.com/documentation/coreservices
      // /1455376-fseventstreamcreateflags/kfseventstreamcreateflagnodefer?language=objc
      //
      0x00000002
  )

  private[this] var current: CFRunLoopRef = null

  def run() = {
    assert(!closed)
    CarbonApi.INSTANCE.FSEventStreamScheduleWithRunLoop(
      streamRef,
      CarbonApi.INSTANCE.CFRunLoopGetCurrent(),
      CFStringRef.toCFString("kCFRunLoopDefaultMode")
    )
    CarbonApi.INSTANCE.FSEventStreamStart(streamRef)
    current = CarbonApi.INSTANCE.CFRunLoopGetCurrent()
    logger("FSLOOP RUN", ())
    CarbonApi.INSTANCE.CFRunLoopRun()
    logger("FSLOOP END", ())
  }

  def close() = {
    assert(!closed)
    closed = true
    logger("FSLOOP STOP", ())
    CarbonApi.INSTANCE.CFRunLoopStop(current)
    CarbonApi.INSTANCE.FSEventStreamStop(streamRef)
    CarbonApi.INSTANCE.FSEventStreamUnscheduleFromRunLoop(
      streamRef,
      current,
      CFStringRef.toCFString("kCFRunLoopDefaultMode")
    )
    CarbonApi.INSTANCE.FSEventStreamInvalidate(streamRef)
    CarbonApi.INSTANCE.FSEventStreamRelease(streamRef)
    logger("FSLOOP STOP2", ())
  }
}
