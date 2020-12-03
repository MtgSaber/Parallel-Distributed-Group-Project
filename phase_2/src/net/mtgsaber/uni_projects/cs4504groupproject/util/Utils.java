package net.mtgsaber.uni_projects.cs4504groupproject.util;

import java.util.logging.Level;

public class Utils {

    /**
     * This is a helper method for shutdown()
     */
    public static void joinThreadForShutdown(Thread threadToJoin) {
        if (threadToJoin == null) return;
        try {
            threadToJoin.join(); // TODO: decide on a wait time (Andrew)
        } catch (InterruptedException iex) {
            Logging.log(Level.WARNING, "Took too long to join thread \"" + threadToJoin.getName() + "\".");
            while (!threadToJoin.getState().equals(Thread.State.TERMINATED)) {
                Logging.log(Level.WARNING, "Interrupting thread \"" + threadToJoin.getName() + "\".");
                threadToJoin.interrupt();
            }
        }
    }
}
