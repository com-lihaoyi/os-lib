package os.watch

abstract class Watcher extends AutoCloseable{
  def start(): Unit
}
