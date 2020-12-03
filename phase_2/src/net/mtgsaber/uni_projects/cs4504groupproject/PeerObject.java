package net.mtgsaber.uni_projects.cs4504groupproject;

import net.mtgsaber.lib.algorithms.Pair;
import net.mtgsaber.lib.events.AsynchronousEventManager;
import net.mtgsaber.lib.events.Event;
import net.mtgsaber.lib.events.EventManager;
import net.mtgsaber.uni_projects.cs4504groupproject.config.Config;
import net.mtgsaber.uni_projects.cs4504groupproject.events.*;
import net.mtgsaber.uni_projects.cs4504groupproject.util.Logging;
import net.mtgsaber.uni_projects.cs4504groupproject.util.SocketAction;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;

public class PeerObject implements Consumer<Event> {
    /* ~~~~~~~~~~~ internal structures and threads. ~~~~~~~~~~~~~~~~~~~~~ */
    private final Config CONFIG; // holds data from the configuration file for this peer

    private final EventManager CENTRAL_EVENT_MANAGER; // serves as the communication medium for main and other peers.

    // handles work that needs to be done by this peer. i.e. starting download sockets, registering files, processing incoming network messages, etc.
    private final AsynchronousEventManager CLIENT_EVENT_MANAGER;
    private final Thread CLIENT_EVENT_MANAGER_THREAD; // thread for this peer's internal event manager

    // dedicated process that waits on a port until a network message is received, then forwards it to this peer's internal event manager and goes back to waiting.
    private final PeerObject_ListeningServer HANDSHAKE_SERVER;
    private final Thread HANDSHAKE_THREAD; // the listening server resides on this thread

    private final Map<Integer, Pair<Thread, Socket>> SOCKETS = new HashMap<>(); // Keeps track of the threads created for sockets;
    private final PriorityQueue<Integer> PORT_QUEUE = new PriorityQueue<>((o1, o2) -> o2 - o1); // This will ensure that lower ports will be at front of queue

    private final Set<Thread> WORKER_THREAD_SET = new HashSet<>(); // to keep track of worker threads

    /* ~~~~~~~~~~~~~ strings calculated at object instantiation ~~~~~~~~~~~~~~~~ */
    public final String INCOMING_CONNECTION_EVENT_NAME;
    public final String DOWNLOAD_COMMAND_EVENT_NAME;
    public final String SHUTDOWN_COMMAND_EVENT_NAME;
    public final String RESOURCE_REGISTRATION_EVENT_NAME;
    private final String[] CENTRAL_EVENT_NAMES;

    // represents a peer with a specific config, internal event management thread, and internal network listening thread
    public PeerObject(Config config, EventManager centralEventManager) {
        this.CONFIG = config; // main reads in config files, then creates this instance with it. better than dumping the workload here with everything else.
        this.CENTRAL_EVENT_MANAGER = centralEventManager; // given by main for communications

        // internal event manager setup
        CLIENT_EVENT_MANAGER = new AsynchronousEventManager();
        CLIENT_EVENT_MANAGER_THREAD = new Thread(CLIENT_EVENT_MANAGER);
        CLIENT_EVENT_MANAGER.setThreadInstance(CLIENT_EVENT_MANAGER_THREAD);

        // calculate event name produced by port listening thread
        this.INCOMING_CONNECTION_EVENT_NAME = CONFIG.SELF.NAME + IncomingConnectionEvent.SUFFIX;

        // set up event names produced by main's UI
        this.DOWNLOAD_COMMAND_EVENT_NAME = CONFIG.SELF.NAME + DownloadCommandEvent.SUFFIX;
        this.SHUTDOWN_COMMAND_EVENT_NAME = CONFIG.SELF.NAME + ShutdownEvent.SUFFIX;
        this.RESOURCE_REGISTRATION_EVENT_NAME = CONFIG.SELF.NAME + ResourceRegistrationEvent.SUFFIX;

        // calculate names of events distributed by central event manager
        CENTRAL_EVENT_NAMES = new String[] {
                DOWNLOAD_COMMAND_EVENT_NAME,
                SHUTDOWN_COMMAND_EVENT_NAME,
                RESOURCE_REGISTRATION_EVENT_NAME,
        };

        hookEventHandlers(); // set up event handlers.

        // fill the port queue with the configured range of ports
        for (int i = config.STARTING_PORT; i < config.PORT_RANGE; i++)
            PORT_QUEUE.add(i); // add the port in question

        // start or first thread this peer will need: a listening handshake server
        HANDSHAKE_THREAD = new Thread(
                HANDSHAKE_SERVER = new PeerObject_ListeningServer(this),
                config.SELF.NAME+"_HandshakeServer"
        );
    }

    /**
     * Registers handlers for events on both this peer's internal event manager AND the central event manager used by main.
     */
    private void hookEventHandlers() {
        // This event is produced by the port listening thread. should be of type IncomingConnectionEvent.
        // The code for using the socket is executed on a different thread to minimize downtime for this peer's internal event manager.
        CLIENT_EVENT_MANAGER.addHandler(INCOMING_CONNECTION_EVENT_NAME, event -> {
            IncomingConnectionEvent e = ((IncomingConnectionEvent) event);
            // TODO: Add any fields necessary to the IncomingConnectionEvent class necessary to open a socket to the remote peer which is trying to connect.

            // this section takes care of actually opening the socket. this.actionAcceptConnection actually uses the socket.
            try {
                openSocket("REMOTE ADDRESS HERE", -1, this::actionAcceptConnection); // TODO: replace first two args with remote ip address and remote port.
            } catch (IOException ioex) {
                //TODO: handle the exception
            }
        });

        // This event is produced by the main thread's UI. Implementation is delegated to the performDownload() method farther down.
        CLIENT_EVENT_MANAGER.addHandler(
                DOWNLOAD_COMMAND_EVENT_NAME,
                event -> performDownload((DownloadCommandEvent) event)
        );

        // This event is produced by the main thread's UI. Implementation is delegated to the shutdown() method farther down.
        CLIENT_EVENT_MANAGER.addHandler(SHUTDOWN_COMMAND_EVENT_NAME, event -> shutdown());

        // This event is produced by the main thread's UI. Implementation is delegated to the registerResource() method farther down.
        CLIENT_EVENT_MANAGER.addHandler(
                RESOURCE_REGISTRATION_EVENT_NAME,
                event -> registerResource(((ResourceRegistrationEvent) event))
        );
    }

    /**
     * Simple Getter method.
     * @return The name of this peer as defined by the SELF_PARAMS section of the configuration file.
     */
    public String getName() { return CONFIG.SELF.NAME; }

    /**
     * Creates a socket object for use in other methods.
     * @param address the remote address of the host to connect to.
     * @param remotePort the remote port of the host to connect to. This should be the handshake port.
     * @param action A lambda to "consume" the socket. This should use the socket and properly shut it down before terminating.
     * @throws IOException if the socket could not be created.
     */
    private void openSocket(String address, int remotePort, SocketAction action)
            throws IOException {
        if (PORT_QUEUE.isEmpty()) throw new IOException("Client overloaded; No more ports available!");
        int localPort = PORT_QUEUE.remove();
        Socket socket = new Socket(address, remotePort, null, localPort);
        Thread socketThread = new Thread(() -> {
            action.useSocket(socket);
            try {
                socket.close();
                synchronized (SOCKETS) {
                    SOCKETS.remove(localPort);
                }
                PORT_QUEUE.add(localPort);
            } catch (IOException e) {
                Logging.log(Level.SEVERE, "Exception while closing socket on port " + socket.getLocalPort() + ": \"" + e.getMessage()+ "\"");
            }
        });
        synchronized (SOCKETS) {
            SOCKETS.put(localPort, new Pair<>(socketThread, socket));
        }
        socketThread.start();
    }

    /**
     * This is used to push events onto this peer's event queue. Used by the listening thread and by the central event manager.
     * @param e the event to process.
     */
    @Override
    public void accept(Event e) {
        CLIENT_EVENT_MANAGER.push(e);
    }

    /**
     * Simple getter method.
     * See Main.createPeer() for how this is used.
     * Should only be used there.
     * @return A copy of the calculated names of the central event manager events for which this peer will handle via this.accept().
     */
    public String[] getCentralEventNames() {
        return Arrays.copyOf(CENTRAL_EVENT_NAMES, CENTRAL_EVENT_NAMES.length);
    }

    /**
     * Closes and shuts down any structures and threads used by this peer. Should block until all resources are closed.
     * Not yet fully implemented.
     */
    private void shutdown() {
        // TODO: clean up resources for shutdown (Andrew).
        // join all worker threads and socket threads or kill them if they take too long.
        for (Thread worker : WORKER_THREAD_SET) {
            try {
                worker.join(); // TODO: decide on a wait time
            } catch (InterruptedException iex) {
                Logging.log(Level.WARNING, "Took too long to join thread \"" + worker.getName() + "\".");
                while (!worker.getState().equals(Thread.State.TERMINATED)) {
                    Logging.log(Level.WARNING, "Interrupting thread \"" + worker.getName() + "\".");
                    worker.interrupt();
                }
            }
        }
        for (Pair<Thread, Socket> socketThread : SOCKETS.values()) {
            try {
                socketThread.KEY.join(); // TODO: decide on a wait time
            } catch (InterruptedException iex) {
                Logging.log(Level.WARNING, "Took too long to join thread \"" + socketThread.KEY.getName() + "\".");
                while (!socketThread.KEY.getState().equals(Thread.State.TERMINATED)) {
                    Logging.log(Level.WARNING, "Interrupting thread \"" + socketThread.KEY.getName() + "\".");
                    socketThread.KEY.interrupt();
                }
            }
        }
        HANDSHAKE_SERVER.shutdown(HANDSHAKE_THREAD);
        CLIENT_EVENT_MANAGER.shutdown();
        CONFIG.saveToFile();
    }

    /**
     * This should be called before trying to use this peer. It is already taken care of in Main.createPeer().
     */
    public void start() {
        CLIENT_EVENT_MANAGER_THREAD.start();
        HANDSHAKE_THREAD.start();
        Logging.log(Level.INFO, "Peer \"" + CONFIG.SELF.NAME + "\" of group \"" + CONFIG.SELF.GROUP + "\" started.");
    }

    /**
     * This is a lambda method that uses the provided socket to send the contents of a file to a remote peer.
     * The socket has already been used to decide which action to call, and this is the action to call.
     */
    private void uploadFile(Socket socket) {
        // TODO: upload file to remote peer
    }

    /**
     * Like the above method, but this one uses the socket to communicate this superpeer's response to a routing table lookup request.
     * The socket has already been used to decide which action to call, and this is the action to call.
     */
    private void processRoutingRequest(Socket socket) {
        PeerRoutingData routingData = CONFIG.getPeer("INSERT REQUESTED PEER NAME HERE");
        if (routingData != null) {
            // TODO: give the routing data to the requesting peer.
        } else {
            // this could block for a little bit, so make sure the socket doesn't close. whatever that entails.
            routingData = issueRoutingRequest("INSERT REQUESTED PEER NAME HERE", "INSERT REQUESTED PEER GROUP HERE");
            if (routingData != null) {
                // TODO: respond through this new socket to the routing request that was received
            } else {
                // TODO: the data cannot be found. respond accordingly.
            }
        }
    }

    /**
     * This is the initial socket action for any and all incoming connections.
     * Call this in the handler for IncomingConnectionEvent events under the hookEvents() method.
     * @param socket Should be a newly created socket.
     */
    private void actionAcceptConnection(Socket socket) {
        // TODO: use the socket to determine what kind of request this is.

        if (true) { // TODO: replace with legitimate condition. This is just an example of what to do once the type of request has been determined.
            uploadFile(socket);
        } else if (true) { // TODO: replace with legitimate condition. This is just an example of what to do once the type of request has been determined.
            processRoutingRequest(socket);
        }

        // TODO: let remote peer know that this peer is done communicating. Don't call .close() on the socket though, that is reserved for the openSocket() method.
    }

    /**
     * This method will, through various means, return the PeerRoutingData of the requested remote peer, null if it cannot be found.
     * THIS METHOD WILL BLOCK UNTIL THE DATA IS RETRIEVED.
     * For superpeers:
     *      this first checks the internal routing table. If it isn't there, it forwards this to the correct superpeer.
     *      If this is no superpeer for the remote peer's group, null is returned.
     *
     * For non-super peers:
     *      this first checks the cache for a non-expired entry of the requested peer.
     *      If it isn't cached, it forwards the request to this peer's local superpeer.
     *
     * This is used by the performDownload() and processRoutingRequest() methods.
     * @param remoteClientName the name of the peer whose routing data we are requesting
     * @param remoteClientGroup the name of the group that the target peer is a member of
     * @return the PeerRoutingData of the target peer, null if it could not be found.
     */
    private PeerRoutingData issueRoutingRequest(String remoteClientName, String remoteClientGroup) {
        PeerRoutingData peerRoutingData = CONFIG.getPeer(remoteClientName);
        if (peerRoutingData == null) {
            if (CONFIG.SELF.IS_SUPER_PEER) {
                // TODO: open a socket to send a routing request to the appropriate superpeer through
                PeerRoutingData remoteSuperPeerRoutingData = CONFIG.getSuperPeer(remoteClientGroup);
                if (remoteSuperPeerRoutingData == null) return null;
                try {
                    openSocket(null, -1, socket -> { // the address should be that of the remote group's superpeer. might need to add a map for fellow superpeers.
                        // TODO: use the socket to communicate
                    });
                } catch (IOException ioex) {
                    // TODO: process the exception
                    // maybe push an event to the central event manager so that the UI can report the error to the user?
                }
            } else {
                try {
                    openSocket(CONFIG.LOCAL_SUPER_PEER.IP_ADDRESS, CONFIG.LOCAL_SUPER_PEER.HANDSHAKE_PORT, socket -> {
                        // TODO: use the socket to make the request.
                    });
                } catch (IOException ioex) {
                    // TODO: process the exception
                    // maybe push an event to the central event manager so that the UI can report the error to the user?
                }
            }
        }
        if (peerRoutingData != null) CONFIG.addPeer(peerRoutingData);
        return peerRoutingData;
    }

    /**
     * Used as the handler for DownloadCommandEvent events from main.
     * This method spawns a worker thread to take care of the download overhead.
     * @param e the download command event.
     */
    private void performDownload(DownloadCommandEvent e) {
        Thread worker = new Thread(() -> { // issueRoutingRequest is blocking, so it's best to keep it separate from the rest of what this peer needs to do.
                try {
                    PeerRoutingData targetPeerRoutingData = issueRoutingRequest(e.REMOTE_PEER_NAME, e.REMOTE_PEER_GROUP);
                    if (targetPeerRoutingData == null) {
                        // TODO: report the failed attempt
                        return;
                    }
                    openSocket(targetPeerRoutingData.IP_ADDRESS, targetPeerRoutingData.HANDSHAKE_PORT, socket -> {
                        // TODO: use socket and the information from e to perform the download.
                    });
                } catch (IOException ioex) {
                    // TODO: handle the exception
                    // maybe push an event to the central event manager so that the UI can report the error to the user?
                } finally {
                    synchronized (WORKER_THREAD_SET) {
                        WORKER_THREAD_SET.remove(Thread.currentThread());
                    }
                }
        });
        synchronized (WORKER_THREAD_SET) {
            WORKER_THREAD_SET.add(worker);
        }
        worker.start();
    }

    /**
     * This one is very low priority to implement since it could all be done in config files.
     * @param event
     */
    private void registerResource(ResourceRegistrationEvent event) {
        try {
            if (CONFIG.addResource(event.NAME, event.FILE)) {
                // TODO: push an event to the central event manager to let main() know that it worked
            } else {
                // TODO: push an event to the central event manager to let main() know that it failed
            }
        } catch (FileNotFoundException fnfex) {
            // TODO: push an event to the central event manager to let main() know that it failed
        } catch (TimeoutException toex) {
            // TODO: push an event to the central event manager to let main() know that it failed
        }
    }
}
