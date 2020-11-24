package components;

import net.mtgsaber.uni_projects.cs4504groupproject.util.Logging;

import java.util.Random;
import java.util.logging.Level;

public class Logging_Test2 {
    public static void main(String[] args) {
        Logging.start(System.out, true);
        Logging.setLevelStream(Level.WARNING, System.err);
        Logging.setLevelStream(Level.SEVERE, System.err);
        Thread[] threads = new Thread[10];
        Level[] levels = new Level[]{Level.INFO, Level.WARNING, Level.SEVERE};
        Random seedsRNG = new Random();
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(
                    () -> {
                        Random rng = new Random(seedsRNG.nextLong());
                        for (int j = 0; j < 10000; j++) {
                            Logging.log(levels[rng.nextInt(levels.length)], "Hello World! (" + j + ")");
                            try {
                                Thread.sleep(rng.nextInt(5));
                            } catch (InterruptedException iex) {}
                        }
                    }
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
