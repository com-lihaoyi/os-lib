package os.watch.macos

import com.sun.jna.PointerType

import scala.reflect.ClassTag

object NativeUtils {

  /** Checks if a type-safe pointer is null. */
  def optionOf[A <: PointerType](a: A): Option[A] =
    if (a.getPointer == null) None else Some(a)

  /**
   * Ensures that all elements of `iterable` are [[Some]]. If any of them are [[None]], all the [[Some]] elements are
   * released and then [[None]] is returned.
   */
  def assertAllSome[A: ClassTag](
      array: Array[Option[A]],
      release: A => Unit
  ): Option[Array[A]] = {
    if (array.forall(_.isDefined)) {
      Some(array.map(_.get))
    } else {
      array.foreach(_.foreach(release))
      None
    }
  }
}
