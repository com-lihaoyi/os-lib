package os.watch.macos

import com.sun.jna._

/** @see https://developer.apple.com/documentation/dispatch?language=objc */
object DispatchApi {
  def apply(): DispatchApi = INSTANCE
  val INSTANCE: DispatchApi = Native.load("dispatch", classOf[DispatchApi])
}
trait DispatchApi extends Library {

  /**
   * @param attr The queue type. We always use `null` to indicate a serial queue.
   *
   * @see https://developer.apple.com/documentation/dispatch/1453030-dispatch_queue_create/
   */
  def dispatch_queue_create(label: String, attr: Void): dispatch_queue_t

  def dispatch_queue_create(label: String): dispatch_queue_t = dispatch_queue_create(label, null)

  /** @see https://developer.apple.com/documentation/dispatch/1496328-dispatch_release */
  def dispatch_release(queue: dispatch_queue_t): Unit
}

/**
 * Objective-C definition: `typedef NSObject<OS_dispatch_queue> * dispatch_queue_t;`
 *
 * @see https://developer.apple.com/documentation/dispatch/dispatch_queue_t?language=objc
 **/
class dispatch_queue_t extends PointerType
