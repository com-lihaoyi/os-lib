package os.watch

object watch{
  def apply(roots: Seq[os.Path],
            onEvent: Set[os.Path] => Unit,
            logger: (String, Any) => Unit = (_, _) => ()): AutoCloseable  = {
    val watcher = System.getProperty("os.name") match{
      case "Linux" => new os.watch.WatchServiceWatcher(roots, onEvent, logger)
      case "Mac OS X" => new os.watch.FSEventsWatcher(roots, onEvent, logger, 0.05)
    }

    val thread = new Thread(() => watcher.start())
    thread.setDaemon(true)
    thread.start()
    watcher
  }
}
