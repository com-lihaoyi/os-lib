package os.watch

import com.sun.jna.{NativeLong, Pointer}

class FSEventsWatcher(srcs: Seq[os.Path],
                      onEvent: Set[os.Path] => Unit,
                      logger: (String, Any) => Unit = (_, _) => (),
                      latency: Double) extends Watcher{
  private[this] var closed = false
  val callback = new FSEventStreamCallback{
    def invoke(streamRef: FSEventStreamRef,
               clientCallBackInfo: Pointer,
               numEvents: NativeLong,
               eventPaths: Pointer,
               eventFlags: Pointer,
               eventIds: Pointer) = {
      val length = numEvents.intValue
      val p = eventPaths.getStringArray(0, length)
      logger("FSEVENT", p)
      onEvent(p.map(os.Path(_)).toSet)
    }
  }

  val streamRef = CarbonApi.INSTANCE.FSEventStreamCreate(
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

  var current: CFRunLoopRef = null

  def start() = {
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