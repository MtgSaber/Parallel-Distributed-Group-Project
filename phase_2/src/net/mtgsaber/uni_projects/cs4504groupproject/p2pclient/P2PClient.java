package net.mtgsaber.uni_projects.cs4504groupproject.p2pclient;

import net.mtgsaber.lib.algorithms.Pair;
import net.mtgsaber.lib.events.Event;
import net.mtgsaber.lib.events.EventHandler;
import net.mtgsaber.uni_projects.cs4504groupproject.Config;
import net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.events.DownloadCommandEvent;
import net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.events.ResourceRegistrationEvent;
import net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.events.ShutdownEvent;
import net.mtgsaber.uni_projects.cs4504groupproject.data.Peer;
import net.mtgsaber.uni_projects.cs4504groupproject.packets.P2PTransferRequest;
import net.mtgsaber.uni_projects.cs4504groupproject.packets.routinglookup.RoutingLookupRequest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class P2PClient implements Runnable, EventHandler {
    private Thread mainThread;
    private boolean mainThreadSet = false;

    private final Config CONFIG;

    private final Thread HANDSHAKE_THREAD;
    private final P2PClient_ListeningServer HANDSHAKE_SERVER;

    private final Queue<Event> EVENT_QUEUE = new LinkedBlockingQueue<>();
    private final Map<Integer, Pair<Thread, Socket>> SOCKETS = new HashMap<>();
    private final PriorityQueue<Integer> PORT_QUEUE = new PriorityQueue<>((o1, o2) -> o2 - o1); // This will ensure that lower ports will be at front of queue

    public final String ROUTING_REQUEST_EVENT_NAME;
    public final String TRANSFER_REQUEST_EVENT_NAME;
    public final String DOWNLOAD_COMMAND_EVENT_NAME;
    public final String SHUTDOWN_COMMAND_EVENT_NAME;
    public final String RESOURCE_REGISTRATION_EVENT_NAME;
    private final String[] EVENT_NAMES;

    public P2PClient(Config config) {
        this.CONFIG = config;

        // event names
        this.ROUTING_REQUEST_EVENT_NAME = CONFIG.SELF.NAME + "_RoutingRequestReceived";
        this.TRANSFER_REQUEST_EVENT_NAME = CONFIG.SELF.NAME + "_TransferRequestReceived";
        this.DOWNLOAD_COMMAND_EVENT_NAME = CONFIG.SELF.NAME + DownloadCommandEvent.SUFFIX;
        this.SHUTDOWN_COMMAND_EVENT_NAME = CONFIG.SELF.NAME + ShutdownEvent.SUFFIX;
        this.RESOURCE_REGISTRATION_EVENT_NAME = CONFIG.SELF.NAME + ResourceRegistrationEvent.SUFFIX;
        EVENT_NAMES = new String[] {
                ROUTING_REQUEST_EVENT_NAME,
                TRANSFER_REQUEST_EVENT_NAME,
                DOWNLOAD_COMMAND_EVENT_NAME,
                SHUTDOWN_COMMAND_EVENT_NAME,
                RESOURCE_REGISTRATION_EVENT_NAME,
        };

        for (int i = config.STARTING_PORT; i < config.PORT_RANGE; i++) // fill the port queue with our range of ports
            PORT_QUEUE.add(i);

        HANDSHAKE_THREAD = new Thread( // set up thead for listening on the handshake port
                HANDSHAKE_SERVER = new P2PClient_ListeningServer(this),
                config.SELF.NAME+"_HandshakeServer"
        );
    }

    public String getName() { return CONFIG.SELF.NAME; }

    /**
     * Main service loop of this client.
     * This manages the TCP Socket threads, the event queue, handshakes, file uploads, file downloads,
     * and routing requests if this is a superpeer.
     *
     * This is essentially just an event processor.
     */
    @Override
    public void run() {
        boolean running = true;

        HANDSHAKE_THREAD.start();

        while (running) {
            try {
                mainThread.wait();
            } catch (InterruptedException interruptedEx) {
                // TODO: check the event buffer and handle the events.
                while (!EVENT_QUEUE.isEmpty() && running) {
                    Event e = EVENT_QUEUE.remove();
                    if (e.getName().equals(ROUTING_REQUEST_EVENT_NAME)) {
                        RoutingRequestReceivedEvent event = ((RoutingRequestReceivedEvent) e);
                        //TODO: open up a socket and pass the appropriate socket action
                    } else if (e.getName().equals(TRANSFER_REQUEST_EVENT_NAME)) {
                        ResourceRegistrationEvent event = ((ResourceRegistrationEvent) e);
                        //TODO: open up a socket and pass the appropriate socket action
                    } else if (e.getName().equals(DOWNLOAD_COMMAND_EVENT_NAME)) {
                        DownloadCommandEvent event = ((DownloadCommandEvent) e);
                        // TODO: make request to remote client to begin download
                    } else if (e.getName().equals(RESOURCE_REGISTRATION_EVENT_NAME)) {
                        ResourceRegistrationEvent event = ((ResourceRegistrationEvent) e);
                        // TODO: attempt to register the resource if the file is not already registered.
                    } else if (e.getName().equals(SHUTDOWN_COMMAND_EVENT_NAME)) {
                        running = false;
                    }
                }
            }
        }

        shutdown();
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
     * This is used to push events onto this client's event queue.
     * @param e the event to process.
     */
    @Override
    public void handle(Event e) {
        EVENT_QUEUE.add(e);
        mainThread.interrupt();
    }

    public String[] getEventNames() {
        return Arrays.copyOf(EVENT_NAMES, EVENT_NAMES.length);
    }

    public void offerThreadReference(Thread thread) {
        if (!mainThreadSet) {
            mainThread = thread;
            mainThreadSet = true;
        }
    }

    private void shutdown() {
        // TODO: clean up resources for thread shutdown.
        CONFIG.saveToFile();
    }

    private void actionDownloadFile(Socket socket) {
        // TODO: download file from remote client
    }

    private void actionUploadFile(Socket socket) {
        // TODO: upload file to remote client
    }

    private void actionProcessRoutingRequest(Socket socket) {
        // TODO: process the routing request
    }

    private Peer issueRoutingRequest(String remoteClientName, String remoteClientGroup) {
        Peer peer = CONFIG.getPeer(remoteClientName);
        if (peer == null) {
            if (CONFIG.SELF.IS_SUPER_PEER) {
                // TODO: send a routing request to the appropriate superpeer
            } else {
                // TODO: send a routing request to this client's superpeer
            }
        }
        if (peer != null) CONFIG.addPeer(peer);
        return peer;
    }

    public final class RoutingRequestReceivedEvent implements Event {
        public final RoutingLookupRequest PACKET;

        public RoutingRequestReceivedEvent(RoutingLookupRequest packet) {
            this.PACKET = packet;
        }

        @Override
        public String getName() {
            return ROUTING_REQUEST_EVENT_NAME;
        }
    }
    public final class FileTransferRequestReceivedEvent implements Event {
        public final P2PTransferRequest PACKET;

        public FileTransferRequestReceivedEvent(P2PTransferRequest packet) {
            this.PACKET = packet;
        }

        @Override
        public String getName() {
            return TRANSFER_REQUEST_EVENT_NAME;
        }
    }
}
