package os.watch

case class Locked[A <: AnyRef](private val it : A) {
  def apply[B](f: A => B): B = it.synchronized {
    f(it)
  }

}
