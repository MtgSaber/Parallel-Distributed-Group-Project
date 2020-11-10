package net.mtgsaber.uni_projects.cs4504groupproject;

import net.mtgsaber.lib.algorithms.Pair;
import net.mtgsaber.lib.algorithms.trees.binary.Heap;
import net.mtgsaber.lib.events.Event;
import net.mtgsaber.lib.events.EventHandler;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class P2PClient implements Runnable, EventHandler {
    public static final String EVENT_SUFFIX = "_FileDownload";

    private final Config CONFIG;
    private final Queue<FileDownloadEvent> FILE_DOWNLOAD_QUEUE = new LinkedBlockingQueue<>();

    private final Map<Integer, Pair<Thread, Socket>> SOCKETS = new HashMap<>();
    private final PriorityQueue<Integer> PORT_QUEUE = new PriorityQueue<>((o1, o2) -> o2 - o1); // This will ensure that lower ports will be at front of queue

    private volatile boolean running;

    public P2PClient(Config config) {
        this.CONFIG = config;
        for (int i = config.STARTING_PORT; i < config.PORT_RANGE; i++)
            PORT_QUEUE.add(i);
    }

    public String getName() { return CONFIG.SELF.NAME; }

    /**
     * Main service loop of this client.
     * This manages the TCP Socket threads, the download queue, handshakes, file uploads, file downloads,
     * and routing requests if this is a superpeer.
     */
    @Override
    public void run() {
        running = true;
        while (running) {
            // TODO: client service
            
        }
        // TODO: socket cleanup, thread cleanup, queue cleanup, save config.
    }

    private void openSocket(InetAddress address, short remotePort, SocketAction action)
            throws IOException {
        int localPort = PORT_QUEUE.remove(); // TODO: check if queue is empty, if so throw an exception.
        Socket socket = new Socket(address, remotePort, null, localPort);
        Thread socketThread = new Thread(() -> {
            action.useSocket(socket);
            synchronized (SOCKETS) {
                SOCKETS.remove(localPort);
            }
            synchronized (PORT_QUEUE) {
                PORT_QUEUE.add(localPort);
            }
        });
        synchronized (SOCKETS) {
            SOCKETS.put(localPort, new Pair<>(socketThread, socket));
        }
        socketThread.start();
    }

    /**
     * This is used to enable the main thread to initiate file downloads on this p2p client.
     * @param e the command for this client to complete.
     */
    @Override
    public void handle(Event e) {
        if (e.getName().equals(CONFIG.SELF.NAME + EVENT_SUFFIX)) {
            FILE_DOWNLOAD_QUEUE.add(((FileDownloadEvent) e));
        }
    }

    public static final class FileDownloadEvent implements Event {
        public final String LOCAL_CLIENT_NAME;
        public final File LOCAL_FILE_DESTINATION;
        public final String REMOTE_PEER_NAME;
        public final String REMOTE_PEER_GROUP;
        public final String REMOTE_RESOURCE;

        public FileDownloadEvent(
                String localClientName, File localFileDestination,
                String remotePeerName, String remotePeerGroup, String remoteResource
        ) {
            this.LOCAL_CLIENT_NAME = localClientName;
            this.LOCAL_FILE_DESTINATION = localFileDestination;
            this.REMOTE_PEER_NAME = remotePeerName;
            this.REMOTE_PEER_GROUP = remotePeerGroup;
            this.REMOTE_RESOURCE = remoteResource;
        }

        @Override
        public String getName() {
            return LOCAL_CLIENT_NAME + EVENT_SUFFIX;
        }
    }

    public interface SocketAction { void useSocket(Socket socket); }
}
