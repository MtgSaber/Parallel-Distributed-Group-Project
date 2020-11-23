package net.mtgsaber.uni_projects.cs4504groupproject.util;

import net.mtgsaber.lib.algorithms.Pair;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class Logging {
    private static volatile boolean started;
    private static PrintStream defaultPS;
    private static final Map<Level, Pair<Container<Boolean>, PrintStream>> LEVEL_TO_PS_MAP = new HashMap<>();

    public static boolean start(OutputStream defaultOS) {
        if (started) return false;
        defaultPS = new PrintStream(defaultOS);
        started = true;
        return true;
    }

    public static void log(Level level, String message) {
        if (!started) return;
        /*
        Pair<Object, PSManager> target;
        synchronized (LEVEL_TO_PS_MAP) {
            target = LEVEL_TO_PS_MAP.get(level);
            if (target == null) LEVEL_TO_PS_MAP.put(level, (target = new Pair<>(new Object(), null)));
        }
        if (target.VAL == null || !target.VAL.log(message))
            logToPS(defaultPS, level, message);
        */
        PrintStream ps;
        Pair<Container<Boolean>, PrintStream> pair;
        synchronized (LEVEL_TO_PS_MAP) {
            pair = LEVEL_TO_PS_MAP.get(level);
            if (pair == null)
                LEVEL_TO_PS_MAP.put(
                        level,
                        pair = (new Pair<>(new Container<>(Boolean.TRUE), null))
                );
        }
        ps = pair.VAL == null? defaultPS : pair.VAL;
        synchronized (pair.KEY) {
            if (pair.KEY.get())
                ps.println("[" + Thread.currentThread().getName() + "] [" + level.getLocalizedName() + "]: " + message);
        }
    }

    public static boolean setLevelStream(Level level, OutputStream os) {
        synchronized (LEVEL_TO_PS_MAP) {
            Pair<Container<Boolean>, PrintStream> pair = LEVEL_TO_PS_MAP.get(level);
            if (pair == null)
                LEVEL_TO_PS_MAP.put(
                        level,
                        pair = (new Pair<>(new Container<>(Boolean.TRUE), null))
                );
            synchronized (pair.KEY) {
                if (pair.VAL != null) return false;
                LEVEL_TO_PS_MAP.put(level, new Pair<>(pair.KEY, new PrintStream(os)));
            }
        }
        return true;
    }

    public static boolean shutdown() {
        if (!started) return false;
        // close all print streams
        synchronized (LEVEL_TO_PS_MAP) {
            for (Pair<Container<Boolean>, PrintStream> pair : LEVEL_TO_PS_MAP.values())
                synchronized (pair.KEY) {
                    if (pair.KEY.get())
                        pair.VAL.close();
                    pair.KEY.set(Boolean.FALSE);
                }
            LEVEL_TO_PS_MAP.clear();
            started = false;
        }
        return true;
    }

    /*
    private static final class PSManager {
        private final AsynchronousEventManager MANAGER = new AsynchronousEventManager();
        private final Thread MANAGER_THREAD;
        private final PrintStream OUT;
        private volatile boolean running = true;

        public final Level LEVEL;

        private static final Set<Level> LEVELS = new HashSet<>();

        public PSManager(Level level, OutputStream os) throws LevelAlreadyBoundException {
            synchronized (LEVELS) {
                if (LEVELS.contains(level)) throw new LevelAlreadyBoundException(level);
                LEVELS.add(level);
            }
            this.LEVEL = level;
            this.OUT = new PrintStream(os);

            MANAGER.addHandler(new LoggingEvent(level, "").getName(), this::loggingHandler);
            MANAGER.addHandler(PSManagerShutdownEvent.NAME, event -> {
                OUT.close();
                MANAGER.shutdown();
            });

            MANAGER_THREAD = new Thread(MANAGER, "LoggingThread_" + level.getName());
            MANAGER.setThreadInstance(MANAGER_THREAD);
            MANAGER_THREAD.start();
        }

        public boolean log(String message) {
            if (!running) return false;
            MANAGER.push(new LoggingEvent(LEVEL, message));
            return true;
        }

        public void shutdown() {
            running = false;
            MANAGER.push(new PSManagerShutdownEvent());
            MANAGER.shutdown();
        }

        private void loggingHandler(Event event) {
            if (!(event instanceof LoggingEvent)) return;
            LoggingEvent e = ((LoggingEvent) event);
            // do actual logging.
        }

        private static final class PSManagerShutdownEvent implements Event {
            public static final String NAME = "Shutdown";

            @Override
            public String getName() {
                return NAME;
            }
        }
    }

    public static final class LevelAlreadyBoundException extends Exception {
        public final Level LEVEL;

        public LevelAlreadyBoundException(Level level) {
            super("Logging level \"" + level.getName() + "\" already bound to a management thread!");
            this.LEVEL = level;
        }
    }

    private static final class LoggingEvent implements Event {
        public final Level LEVEL;
        public final String MESSAGE;

        public static final String SUFFIX = "_LoggingEvent";

        public LoggingEvent(Level level, String message) {
            this.LEVEL = level;
            this.MESSAGE = message;
        }

        @Override
        public String getName() {
            return LEVEL + SUFFIX;
        }
    }
    */
}
