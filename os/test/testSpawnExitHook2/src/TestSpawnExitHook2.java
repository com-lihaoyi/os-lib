package test.os;
import java.nio.file.StandardOpenOption;

public class TestSpawnExitHook2{
  public static void main(String[] args) throws Exception{
    java.nio.channels.FileChannel.open(
            java.nio.file.Paths.get(args[0]),
            java.util.EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE)
    ).lock();
    Thread.sleep(1337000);
  }
}
