package components;

import net.mtgsaber.uni_projects.cs4504groupproject.util.Logging;

import java.util.logging.Level;

public class Logging_Test1 {
    public static void main(String[] args) {
        Logging.start(System.out, true);
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(
                    () -> {
                        for (int j = 0; j < 100; j++)
                            Logging.log(Level.INFO, "Hello World! (" + j + ")");
                    },
                    "TestingThread" + i
            );
        }
        long startTime = System.currentTimeMillis(), endTime;
        for (Thread thread : threads) thread.start();
        try {
            for (Thread thread : threads) thread.join();
            endTime = System.currentTimeMillis();
            Logging.log(Level.INFO, "Took " + (endTime - startTime) + "ms!");
        } catch (InterruptedException iex) {
            Logging.log(Level.SEVERE, iex.getMessage());
        }
    }
}
