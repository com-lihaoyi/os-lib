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
          catch { case NonFatal(_) => /*do nothing*/ }
        }
      }
      onEvent((paths.iterator ++ nestedPaths.iterator).toSet)
    }
  }

  // Not sure why this is necessary, but without it the `CarbonApi()` calls in the `run()`
  // method running on a separate thread hang for unknown reasons.
  val dummyCfString = CFStringRef.toCFString("dummy")

  private[this] var current: CFRunLoopRef = null

  def run() = {
    assert(!closed)
    val cfStrings = srcs.map(p => CFStringRef.toCFString(p.toString).getPointer).toArray
    val cfArray =
      CarbonApi().CFArrayCreate(null, cfStrings, CFIndex.valueOf(srcs.length), null)
    val kCFRunLoopDefaultMode = CFStringRef.toCFString("kCFRunLoopDefaultMode")
    val streamRef = CarbonApi().FSEventStreamCreate(
      Pointer.NULL,
      callback,
      Pointer.NULL,
      cfArray,
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

    current = CarbonApi().CFRunLoopGetCurrent()
    CarbonApi().FSEventStreamScheduleWithRunLoop(
      streamRef,
      current,
      kCFRunLoopDefaultMode
    )
    CarbonApi().FSEventStreamStart(streamRef)
    logger("FSLOOP RUN", ())
    try CarbonApi().CFRunLoopRun()
    finally {
      CarbonApi().FSEventStreamStop(streamRef)

      CarbonApi().FSEventStreamUnscheduleFromRunLoop(
        streamRef,
        current,
        kCFRunLoopDefaultMode
      )
      CarbonApi().FSEventStreamInvalidate(streamRef)
      CarbonApi().FSEventStreamRelease(streamRef)
      CarbonApi().CFRelease(cfArray)
      for (s <- cfStrings) CarbonApi().CFRelease(s)
      CarbonApi().CFRelease(kCFRunLoopDefaultMode)
    }
    logger("FSLOOP END", ())
  }

  def close() = {
    assert(!closed)
    closed = true
    logger("FSLOOP CLOSE", ())

    CarbonApi().CFRunLoopStop(current)
    CarbonApi().CFRelease(dummyCfString)

    logger("FSLOOP CLOSE END", ())
  }
}
