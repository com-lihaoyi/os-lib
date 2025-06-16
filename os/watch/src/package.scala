package os

import java.util.UUID
import scala.concurrent.TimeoutException
import scala.util.Random
import scala.concurrent.duration._
import scala.util.control.NonFatal

package object watch {

  /**
   * Efficiently watches the given `roots` folders for changes. Note that these folders need
   * to exist before the method is called.
   *
   * Any time the filesystem is modified within those folders, the `onEvent` callback is
   * called with the paths to the changed files or folders.
   *
   * Once the call to `watch` returns, `onEvent` is guaranteed to receive a
   * an event containing the path for:
   *
   * - Every file or folder that gets created, deleted, updated or moved
   *   within the watched folders
   *
   * - For copied or moved folders, the path of the new folder as well as
   *   every file or folder within it.
   *
   * - For deleted or moved folders, the root folder which was deleted/moved,
   *   but *without* the paths of every file that was within it at the
   *   original location
   *
   * Note that `watch` does not provide any additional information about the
   * changes happening within the watched roots folder, apart from the path
   * at which the change happened. It is up to the `onEvent` handler to query
   * the filesystem and figure out what happened, and what it wants to do.
   *
   * @param onEvent a callback that is called with the paths to the changed. Only starts emitting events once this
   *                method returns.
   * @param filter when new paths under `roots` are created, this function is
   *               invoked with each path. If it returns `false`, the path is
   *               not watched.
   */
  def watch(
    roots: Seq[os.Path],
    onEvent: Set[os.Path] => Unit,
    logger: (String, Any) => Unit = (_, _) => (),
    filter: os.Path => Boolean = _ => true
  ): AutoCloseable = {
    val errors = roots.iterator.flatMap { path =>
      if (!os.exists(path)) Some(s"Root path does not exist: $path")
      else if (!os.isDir(path)) Some(s"Root path is not a directory: $path")
      else None
    }.toVector

    if (errors.nonEmpty) {
      throw new IllegalArgumentException(errors.mkString("\n"))
    }

    val sentinelFiles = roots.iterator.map(_ / s".os-lib-watch-sentinel-${UUID.randomUUID()}").toSet
    val notPickedUpSentinelFiles = collection.mutable.Set.empty[os.Path]
    notPickedUpSentinelFiles ++= sentinelFiles

    def onEvent0(changed: Set[os.Path]): Unit = {
      if (notPickedUpSentinelFiles.isEmpty) {
        // All sentinels have been picked up, resume normal operation
        onEvent(changed)
      }
      else {
        // Wait for all sentinels to be picked up
        changed.foreach(notPickedUpSentinelFiles.remove)
      }
    }

    val watcher = System.getProperty("os.name") match {
      case "Mac OS X" => new os.watch.macos.FSEventsWatcher(roots, onEvent0, filter, logger, latency = 0.05)
      case _ => new os.watch.WatchServiceWatcher(roots, onEvent0, filter, logger)
    }

    val thread = new Thread {
      override def run(): Unit = {
        try {
          watcher.run()
        }
        catch {
          case NonFatal(t) =>
            logger("EXCEPTION", t)
            Console.err.println(
              s"""Watcher thread failed:
                 |  roots = $roots
                 |  exception = $t""".stripMargin
            )
        }
      }
    }
    thread.setDaemon(true)
    thread.start()

    logger("WAITING FOR SENTINELS", sentinelFiles)
    sentinelFiles.foreach(p => waitUntilWatchIsSetUp(p, () => !notPickedUpSentinelFiles.contains(p)))
    logger("SENTINELS PICKED UP", sentinelFiles)

    watcher
  }

  private def waitUntilWatchIsSetUp(sentinelFile: os.Path, wasPickedUp: () => Boolean): Unit = {
    def writeSentinel() =
      os.write.over(sentinelFile, Random.nextLong().toString)

    try {
      writeSentinel()
      val timeout = 5.seconds
      val timeoutNanos = timeout.toNanos
      val start = System.nanoTime()
      while (!wasPickedUp()) {
        val taken = System.nanoTime() - start
        if (taken >= timeoutNanos)
          throw new TimeoutException(
            s"can't set up watch, no file system changes detected within $timeout for sentinel file $sentinelFile"
          )
        Thread.sleep(5)

        writeSentinel()
      }
    }
    finally {
      os.remove(sentinelFile)
    }
  }
}
