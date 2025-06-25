package os

import java.util.UUID
import scala.concurrent.TimeoutException
import scala.util.Random
import scala.concurrent.duration._
import scala.util.control.NonFatal

package object watch {
  private type OnEvent = Set[os.Path] => Unit

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

    @volatile var customOnEvent: OnEvent = onEvent
    // Needed because the function passed to the watcher implementation is stable and we need to change it
    // upon every call.
    val onEvent0: OnEvent = paths => customOnEvent(paths)

    val watcherEither = System.getProperty("os.name") match {
      case "Mac OS X" =>
        Left(new os.watch.macos.FSEventsWatcher(roots, onEvent0, filter, logger, latency = 0.05))
      case _ => Right(new os.watch.WatchServiceWatcher(roots, onEvent0, filter, logger))
    }
    val watcher = watcherEither.merge

    val thread = new Thread {
      override def run(): Unit = {
        try {
          watcher.run()
        } catch {
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
    sentinelFiles.foreach { sentinel =>
      waitUntilWatchIsSetUp(
        sentinel,
        setCustomOnEvent = {
          case Some(custom) => customOnEvent = custom
          case None => customOnEvent = onEvent
        },
        logger
      )
    }
    watcherEither match {
      case Left(macWatcher) =>
        // Apply the actual filter once watch has been set up
        macWatcher.sentinelsPickedUp()
      case Right(_) =>
      // nothing is necessary as `filter` for this watcher is only used for filtering out watched subdirectories
      // and thus does not need to be updated
    }
    logger("SENTINELS PICKED UP", sentinelFiles)

    watcher
  }

  private def waitUntilWatchIsSetUp(
      sentinelFile: os.Path,
      setCustomOnEvent: Option[OnEvent] => Unit,
      logger: (String, Any) => Unit
  ): Unit = {
    def writeSentinel() = os.write.over(
      sentinelFile,
      s"""This file was created because Scala `os-lib` library is trying to set up a watch for this
         |directory. It is automatically deleted when the watch is set up.
         |
         |If you are seeing this file, it means that the watch is not being set up correctly.
         |
         |Raise an issue at https://github.com/lihaoyi/os-lib/issues
         |""".stripMargin
    )

    val timeout = 5.seconds
    val timeoutNanos = timeout.toNanos

    def waitUntilPickedUp(
        wasPickedUp: () => Boolean,
        changeType: String,
        afterSleep: () => Unit
    ): Unit = {
      logger(s"WAITING FOR SENTINEL TO BE PICKED UP ($changeType)", sentinelFile)
      val start = System.nanoTime()
      while (!wasPickedUp()) {
        val taken = System.nanoTime() - start
        if (taken >= timeoutNanos)
          throw new TimeoutException(
            s"can't set up watch, no file system changes detected (expected $changeType) within $timeout " +
              s"for sentinel file $sentinelFile"
          )
        Thread.sleep(5)

        afterSleep()
      }
      logger(s"SENTINEL PICKED UP ($changeType)", sentinelFile)
    }

    try {
      logger("WRITING SENTINEL", sentinelFile)
      @volatile var pickedUp = false
      setCustomOnEvent(Some { changed =>
        if (changed.contains(sentinelFile)) pickedUp = true
      })
      writeSentinel()

      waitUntilPickedUp(
        wasPickedUp = () => pickedUp,
        changeType = "write",
        afterSleep = writeSentinel
      )
    } finally {
      @volatile var pickedUp = false
      setCustomOnEvent(Some { changed =>
        if (changed.contains(sentinelFile)) pickedUp = true
      })
      try {
        logger("REMOVING SENTINEL", sentinelFile)
        os.remove(sentinelFile)
        waitUntilPickedUp(
          wasPickedUp = () => pickedUp,
          changeType = "removal",
          afterSleep = () => {}
        )
      } finally {
        setCustomOnEvent(None)
      }
    }
  }
}
