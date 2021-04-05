package os.watch

sealed trait WatchError

case object Overflow extends WatchError
case class InternalError(t: Throwable) extends WatchError
