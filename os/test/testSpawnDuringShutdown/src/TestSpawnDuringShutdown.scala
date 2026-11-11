package test.os

object TestSpawnDuringShutdown {
  def main(args: Array[String]): Unit = {
    // Cleanup code that runs a subprocess while the JVM shuts down (a shutdown hook, or a
    // finalizer run by one, e.g. on Ctrl-C). `destroyOnExit = true` (the default) has
    // os.call/os.spawn register a shutdown hook of their own, which the JVM refuses once
    // it is shutting down.
    Runtime.getRuntime.addShutdownHook(
      new Thread(() => {
        val res = os.call(cmd = ("echo", "spawned during shutdown"))
        System.out.print(res.out.text())
        System.out.flush()
      })
    )
  }
}
