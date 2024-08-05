import java.util.Scanner;

public class TestJarReader {
    public static void main(String[] args) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);
        int readN = Integer.parseInt(args[0]);
        int readSleep = Integer.parseInt(args[1]);
        boolean debugOutput = Boolean.parseBoolean(args[2]);
        int i = 0;
        while(readN == -1 || i < readN) {
            if(debugOutput) {
                System.err.println("At: " + i);
            }
            String read = scanner.nextLine();
            System.out.println("Read: " + read);
            Thread.sleep(readSleep);
            i++;
        }
        scanner.close();
        if(debugOutput) {
            System.err.println("Exiting reader");
        }
    }
}
