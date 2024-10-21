package test.os

object TestSpawnExitHook {
  def main(args: Array[String]): Unit = {
    Runtime.getRuntime.addShutdownHook(
      new Thread(() => {
        for (shutdownDelay <- args.lift(1)) Thread.sleep(shutdownDelay.toLong)
        System.err.println("Shutdown Hook")
      })
    )
    val cmd = (sys.env("TEST_SPAWN_EXIT_HOOK_ASSEMBLY2"), args(0))
    os.spawn(cmd = cmd, destroyOnExit = true)
    Thread.sleep(99999)
  }
}
