package components;

import net.mtgsaber.uni_projects.cs4504groupproject.util.Logging; // custom logging module

import java.util.Random;
import java.util.logging.Level;

public class Logging_Test2 {
    public static void main(String[] args) {
        // start logging and specify default PrintStream, for those not overridden below
        Logging.start(System.out, true); // output to console with timestamps

        // set up the PrintStream instances to use for specific Levels
        Logging.setLevelStream(Level.WARNING, System.err);
        Logging.setLevelStream(Level.SEVERE, System.err);

        Thread[] threads = new Thread[10];
        Level[] levels = new Level[]{Level.INFO, Level.WARNING, Level.SEVERE};
        Random seedsRNG = new Random();

        for (int i = 0; i < threads.length; i++) { // create threads to generate messages at random times
            threads[i] = new Thread( // create a new thread for each thread handle
                    () -> { // implements Run method of Runnable interface; instructions for this thread
                        Random rng = new Random(seedsRNG.nextLong());
                        for (int j = 0; j < 10000; j++) {
                            Logging.log(levels[rng.nextInt(levels.length)], "Hello World! (" + j + ")"); // generate the message on a random printstream
                            try { // wait for 0-5 milliseconds
                                Thread.sleep(rng.nextInt(5));
                            } catch (InterruptedException iex) {}
                        }
                    }
            );
        }

        // start timing and start all threads
        long startTime = System.currentTimeMillis(), endTime;
        for (Thread thread : threads) thread.start();

        try { // wait for them to finish
            for (Thread thread : threads) thread.join();
            endTime = System.currentTimeMillis();
            Logging.log(Level.INFO, "Took " + (endTime - startTime) + "ms!");
        } catch (InterruptedException iex) {
            Logging.log(Level.SEVERE, iex.getMessage());
        }
    }
}
