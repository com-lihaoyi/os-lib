package test.os

object TestSpawnExitHook {
  def main(args: Array[String]): Unit = {
    os.spawn((sys.env("TEST_SPAWN_EXIT_HOOK_ASSEMBLY2"), args(0)), shutdownHook = true)
    Thread.sleep(99999)
  }
}
