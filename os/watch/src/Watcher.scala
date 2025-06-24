package os.watch

abstract class Watcher extends AutoCloseable {
  def run(): Unit
}