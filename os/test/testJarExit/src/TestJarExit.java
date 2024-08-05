import java.util.Scanner;

public class TestJarExit {
    public static void main(String[] args) throws InterruptedException {
        int exitCode = Integer.parseInt(args[0]);
        int exitSleep = Integer.parseInt(args[1]);
        System.err.println("Exiting with code: " + exitCode);
        Thread.sleep(exitSleep);
        System.exit(exitCode);
    }
}
