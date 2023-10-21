import java.util.Scanner;
import java.lang.InterruptedException;

public class TestJarWriter {
    private static boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
    public static void main(String[] args) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);
        int writeN = Integer.parseInt(args[0]);
        int writeSleep = Integer.parseInt(args[1]);
        boolean debugOutput = Boolean.parseBoolean(args[2]);
        int i = 0;
        while(writeN == -1 || i < writeN) {
            System.out.println("Hello " + i);
            if(debugOutput) {
                System.err.println("Written " + i);
            }
            Thread.sleep(writeSleep);
            i++;
        } 
        scanner.close();
        if(debugOutput) {
            System.err.println("Exiting writer");
        }
    }
}
