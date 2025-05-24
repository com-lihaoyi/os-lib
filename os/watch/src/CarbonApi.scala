package os.watch

import com.sun.jna._

import com.sun.jna.ptr.PointerByReference


object CarbonApi {
  def apply() = INSTANCE
  val INSTANCE = Native.load("Carbon", classOf[CarbonApi]).asInstanceOf[CarbonApi]
}

trait FSEventStreamCallback extends Callback {
  def invoke(
      streamRef: FSEventStreamRef,
      clientCallBackInfo: Pointer,
      numEvents: NativeLong,
      eventPaths: Pointer,
      eventFlags: Pointer,
      eventIds: Pointer
  ): Unit
}

trait CarbonApi extends Library {
  def CFRelease(cfTypeRef: Any): Unit

  def CFArrayCreate(
      allocator: CFAllocatorRef, // always set to Pointer.NULL
      values: Array[Pointer],
      numValues: CFIndex,
      callBacks: Void
  ): CFArrayRef

  // always set to Pointer.NULL): CFArrayRef
  def CFStringCreateWithCharacters(
      alloc: Void, //  always pass NULL
      chars: Array[Char],
      numChars: CFIndex
  ): CFStringRef

  def FSEventStreamCreate(
      v: Pointer, // always use Pointer.NULL
      callback: FSEventStreamCallback,
      context: Pointer,
      pathsToWatch: CFArrayRef,
      sinceWhen: Long, // use -1 for events since now
      latency: Double, // in seconds
      flags: Int
  ): FSEventStreamRef

  // 0 is good for now): FSEventStreamRef
  def FSEventStreamStart(streamRef: FSEventStreamRef): Boolean

  def FSEventStreamStop(streamRef: FSEventStreamRef): Unit

  def FSEventStreamInvalidate(streamRef: FSEventStreamRef): Unit
  def FSEventStreamUnscheduleFromRunLoop(
      streamRef: FSEventStreamRef,
      runLoop: CFRunLoopRef,
      runLoopMode: CFStringRef
  ): Unit
  def FSEventStreamRelease(streamRef: FSEventStreamRef): Unit

  def FSEventStreamScheduleWithRunLoop(
      streamRef: FSEventStreamRef,
      runLoop: CFRunLoopRef,
      runLoopMode: CFStringRef
  ): Unit

  def CFRunLoopGetCurrent(): CFRunLoopRef

  def CFRunLoopRun(): Unit

  def CFRunLoopStop(rl: CFRunLoopRef): Unit
}

class CFAllocatorRef extends PointerByReference {}

class CFArrayRef extends PointerByReference {}

@SerialVersionUID(0)
object CFIndex {
  def valueOf(i: Int) = {
    val idx = new CFIndex
    idx.setValue(i)
    idx
  }
}

@SerialVersionUID(0)
class CFIndex extends NativeLong {}

class CFRunLoopRef extends PointerByReference {}

object CFStringRef {
  def toCFString(s: String) = {
    val chars = s.toCharArray
    val length = chars.length
    CarbonApi.INSTANCE.CFStringCreateWithCharacters(null, chars, CFIndex.valueOf(length))
  }
}

class CFStringRef extends PointerByReference {}

class FSEventStreamRef extends PointerByReference {}
