package java.util.concurrent

import scala.collection.mutable
import scala.collection.JavaConverters._

class ConcurrentLinkedQueue[T] {
  private val buffer = new mutable.Queue[T]

  def add(elem: T) = buffer += elem
  def iterator: java.util.Iterator[T] = buffer.iterator.asJava
}
