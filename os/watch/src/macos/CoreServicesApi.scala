package os.watch.macos

import com.sun.jna._

object CoreServicesApi {
  def apply(): CoreServicesApi = INSTANCE
  val INSTANCE: CoreServicesApi = Native.load("CoreServices", classOf[CoreServicesApi])
}
trait CoreServicesApi extends Library {

  /**
   * Objective-C definition:
   * {{{
   * void CFRelease(CFTypeRef cf);
   * typedef const void * CFTypeRef;
   * }}}
   *
   * @see https://developer.apple.com/documentation/corefoundation/1521153-cfrelease
   */
  def CFRelease(cfTypeRef: PointerType): Unit

  /**
   * Objective-C definition:
   * {{{
   * extern CFArrayRef CFArrayCreate(
   *   CFAllocatorRef allocator, const void * * values, CFIndex numValues, const CFArrayCallBacks * callBacks
   * );
   * }}}
   *
   * @param allocator Always pass NULL to use the current default allocator.
   * @see https://developer.apple.com/documentation/corefoundation/cfarraycreate(_:_:_:_:)
   * @return can return NULL if there was a problem creating the array
   */
  def CFArrayCreate(
      allocator: Void,
      values: Array[Pointer],
      numValues: CFIndex,
      callBacks: Void
  ): CFArrayRef

  def CFArrayCreate(values: Array[Pointer]): Option[CFArrayRef] = {
    val numValues = CFIndex(values.length)
    val maybeCfArrayRef = CFArrayCreate(allocator = null, values, numValues, callBacks = null)
    NativeUtils.optionOf(maybeCfArrayRef)
  }

  /**
   * @param alloc Always pass NULL to use the current default allocator.
   * @param chars The buffer of Unicode characters to copy into the new string.
   * @param numChars The number of characters in the buffer pointed to by chars. Only this number of characters will
   *                 be copied to internal storage.
   * @return An immutable string containing chars, or NULL if there was a problem creating the object.
   * @see https://developer.apple.com/documentation/corefoundation/cfstringcreatewithcharacters(_:_:_:)?language=objc
   */
  def CFStringCreateWithCharacters(
      alloc: Void,
      chars: Array[Char],
      numChars: CFIndex
  ): CFStringRef

  def CFStringCreateWithCharacters(chars: Array[Char]): Option[CFStringRef] = {
    val numChars = CFIndex(chars.length)
    val maybeCfStringRef = CFStringCreateWithCharacters(alloc = null, chars, numChars)
    NativeUtils.optionOf(maybeCfStringRef)
  }

  /**
   * @param allocator Always pass NULL to use the current default allocator.
   * @param context A pointer to the FSEventStreamContext structure the client wants to associate with this stream.
   *                Its fields are copied out into the stream itself so its memory can be released after the stream
   *                is created. Passing NULL is allowed and has the same effect as passing a structure whose fields
   *                are all set to zero. We don't use this context, so we only allow passing NULL.
   * @param sinceWhen The time to start watching for events. Use -1 for events since now.
   * @param latency The latency of the stream, in seconds.
   * @param flags The flags for the stream. See
   *              https://developer.apple.com/documentation/coreservices/1455376-fseventstreamcreateflags?language=objc
   * @see https://developer.apple.com/documentation/coreservices/1443980-fseventstreamcreate?language=objc
   */
  def FSEventStreamCreate(
      allocator: Void,
      callback: FSEventStreamCallback,
      context: Void,
      pathsToWatch: CFArrayRef,
      sinceWhen: Long,
      latency: Double,
      flags: Int
  ): FSEventStreamRef

  def FSEventStreamCreate(
      callback: FSEventStreamCallback,
      pathsToWatch: CFArrayRef,
      sinceWhen: Long,
      latency: Double,
      flags: Int
  ): FSEventStreamRef = {
    FSEventStreamCreate(
      allocator = null,
      callback = callback,
      context = null,
      pathsToWatch = pathsToWatch,
      sinceWhen = sinceWhen,
      latency = latency,
      flags = flags
    )
  }

  /**
   * Objective-C definition:
   * {{{
   * void FSEventStreamSetDispatchQueue(FSEventStreamRef streamRef, dispatch_queue_t q);
   * }}}
   *
   * @see https://developer.apple.com/documentation/coreservices/1444164-fseventstreamsetdispatchqueue?language=objc
   */
  def FSEventStreamSetDispatchQueue(streamRef: FSEventStreamRef, queue: dispatch_queue_t): Unit

  /**
   * @return True if it succeeds, otherwise False if it fails. It ought to always succeed, but in the event it does
   *         not then your code should fall back to performing recursive scans of the directories of interest as
   *         appropriate.
   *
   * @see https://developer.apple.com/documentation/coreservices/1448000-fseventstreamstart?language=objc
   */
  def FSEventStreamStart(streamRef: FSEventStreamRef): Boolean

  /**
   * Objective-C definition:
   * {{{
   * void FSEventStreamStop(FSEventStreamRef streamRef);
   * }}}
   *
   * @see https://developer.apple.com/documentation/coreservices/1447673-fseventstreamstop?language=objc
   */
  def FSEventStreamStop(streamRef: FSEventStreamRef): Unit

  /** @see https://developer.apple.com/documentation/coreservices/1446990-fseventstreaminvalidate?language=objc */
  def FSEventStreamInvalidate(streamRef: FSEventStreamRef): Unit

  /** @see https://developer.apple.com/documentation/coreservices/1445989-fseventstreamrelease?language=objc */
  def FSEventStreamRelease(streamRef: FSEventStreamRef): Unit
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

/**
 * Objective-C definition: `typedef const struct __CFArray * CFArrayRef;`
 *
 * @see https://developer.apple.com/documentation/corefoundation/cfarray?language=objc
 */
class CFArrayRef extends PointerType

object CFIndex {
  def apply(value: Long): CFIndex = new CFIndex(value)
}

/** @see https://developer.apple.com/documentation/corefoundation/cfindex */
class CFIndex(value: Long) extends NativeLong(value) {
  // Required by JNA
  def this() = this(0)
}

/**
 * Objective-C definition: `typedef const struct __CFString * CFStringRef;`
 *
 * @see https://developer.apple.com/documentation/corefoundation/cfstring?language=objc
 */
class CFStringRef extends PointerType
object CFStringRef {

  /**
   * @return An immutable string containing chars, or [[None]] if there was a problem creating the object.
   */
  def apply(s: String): Option[CFStringRef] =
    CoreServicesApi.INSTANCE.CFStringCreateWithCharacters(s.toCharArray)
}

/**
 * Objective-C definition: `typedef struct __FSEventStream *FSEventStreamRef;`
 *
 * @see https://developer.apple.com/documentation/coreservices/fseventstreamref
 */
class FSEventStreamRef extends PointerType
