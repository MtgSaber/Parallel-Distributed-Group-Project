package net.mtgsaber.uni_projects.cs4504groupproject;

import net.mtgsaber.lib.events.AsynchronousEventManager;
import net.mtgsaber.lib.events.Event;
import net.mtgsaber.lib.events.EventManager;
import net.mtgsaber.uni_projects.cs4504groupproject.config.PeerObjectConfig;
import net.mtgsaber.uni_projects.cs4504groupproject.events.*;
import net.mtgsaber.uni_projects.cs4504groupproject.util.Container;
import net.mtgsaber.uni_projects.cs4504groupproject.util.Logging;
import net.mtgsaber.uni_projects.cs4504groupproject.util.SocketAction;
import net.mtgsaber.uni_projects.cs4504groupproject.util.Utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.io.BufferedWriter;

public class PeerObject implements Consumer<Event> {
    /* ~~~~~~~~~~~ internal structures and threads. ~~~~~~~~~~~~~~~~~~~~~ */
    private final PeerObjectConfig CONFIG; // holds data from the configuration file for this peer

    private final EventManager CENTRAL_EVENT_MANAGER; // serves as the communication medium for main and other peers.

    // handles work that needs to be done by this peer. i.e. starting download sockets, registering files, processing incoming network messages, etc.
    private final AsynchronousEventManager PEER_EVENT_MANAGER;
    private final Thread PEER_EVENT_MANAGER_THREAD; // thread for this peer's internal event manager

    // dedicated process that waits on a port until a network message is received, then forwards it to this peer's internal event manager and goes back to waiting.
    private final PeerObject_ListeningServer LISTENING_SERVER;
    private final Thread SERVER_THREAD; // the listening server resides on this thread

    private final Set<Thread> SOCKET_THREAD_SET = new HashSet<>(); // Keeps track of the threads created for sockets;
    private final Set<Thread> WORKER_THREAD_SET = new HashSet<>(); // to keep track of worker threads

    // These are for shutdown synchronization
    private final Container<PeerObjectLifecycleStates> STATE = new Container<>(null);
    private final Object SHUTDOWN_BLOCKING_LOCK = new Object();

    /* ~~~~~~~~~~~~~ strings calculated at object instantiation ~~~~~~~~~~~~~~~~ */
    public final String INCOMING_CONNECTION_EVENT_NAME;
    public final String DOWNLOAD_COMMAND_EVENT_NAME;
    public final String SHUTDOWN_COMMAND_EVENT_NAME;
    public final String RESOURCE_REGISTRATION_EVENT_NAME;
    private final String[] CENTRAL_EVENT_NAMES;

    // represents a peer with a specific config, internal event management thread, and internal network listening thread
    public PeerObject(PeerObjectConfig peerObjectConfig, EventManager centralEventManager) {
        this.CONFIG = peerObjectConfig; // main reads in config files, then creates this instance with it. better than dumping the workload here with everything else.
        this.CENTRAL_EVENT_MANAGER = centralEventManager; // given by main for communications

        // internal event manager setup
        PEER_EVENT_MANAGER = new AsynchronousEventManager();
        PEER_EVENT_MANAGER_THREAD = new Thread(
                PEER_EVENT_MANAGER,
                CONFIG.SELF.GROUP + "." + CONFIG.SELF.NAME + "_InternalEventManagerThread"
        );

        // calculate event name produced by port listening thread
        this.INCOMING_CONNECTION_EVENT_NAME = IncomingConnectionEvent.NAME;

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

        /*
        // fill the port queue with the configured range of ports
        for (int i = peerObjectConfig.STARTING_PORT; i < peerObjectConfig.PORT_RANGE; i++)
            PORT_QUEUE.add(i); // add the port in question

         */

        // start or first thread this peer will need: a listening handshake server
        SERVER_THREAD = new Thread(
                LISTENING_SERVER = new PeerObject_ListeningServer(this),
                peerObjectConfig.SELF.GROUP + "." + peerObjectConfig.SELF.NAME+"_HandshakeServer"
        );

        synchronized (STATE) {
            STATE.set(PeerObjectLifecycleStates.READY);
        }
    }

    /**
     * Simple getter.
     * @return the routing data as defined by the configuration for this peer.
     */
    public PeerRoutingData getRoutingData() {
        return CONFIG.SELF;
    }

    /**
     * This should be called before trying to use this peer. It is already taken care of in Main.createPeer().
     */
    public void start() {
        synchronized (STATE) {
            if (STATE.get().equals(PeerObjectLifecycleStates.READY)) {
                PEER_EVENT_MANAGER_THREAD.start();
                SERVER_THREAD.start();
                Logging.log(Level.INFO, "Peer \"" + CONFIG.SELF.NAME + "\" of group \"" + CONFIG.SELF.GROUP + "\" started.");
                STATE.set(PeerObjectLifecycleStates.ALIVE);
            }
        }
    }

    /**
     * Closes and shuts down any structures and threads used by this peer. Should block until all resources are closed.
     * Not yet fully implemented.
     */
    public void shutdown() {
        boolean permissionToPerformShutdownProcedure;
        synchronized (STATE) {
            switch (STATE.get()) {
                case READY -> {
                    STATE.set(PeerObjectLifecycleStates.TERMINATED);
                    return;
                }
                case ALIVE -> {
                    STATE.set(PeerObjectLifecycleStates.TERMINATING);
                    permissionToPerformShutdownProcedure = true;
                }
                case TERMINATING -> {
                    permissionToPerformShutdownProcedure = false;
                }
                default -> {
                    return;
                }
            }
        }
        if (permissionToPerformShutdownProcedure) {
            // join all worker threads and socket threads or kill them if they take too long.
            Logging.log(Level.INFO, "Beginning shutdown procedure for client " + CONFIG.SELF.GROUP + "." + CONFIG.SELF.NAME);
            synchronized (WORKER_THREAD_SET) {
                for (Thread worker : WORKER_THREAD_SET)
                    Utils.joinThreadForShutdown(worker);
            }
            synchronized (SOCKET_THREAD_SET) {
                for (Thread socketThread : SOCKET_THREAD_SET)
                    Utils.joinThreadForShutdown(socketThread);
            }
            LISTENING_SERVER.shutdown(SERVER_THREAD);
            Utils.joinThreadForShutdown(SERVER_THREAD);
            PEER_EVENT_MANAGER.shutdown();
            Utils.joinThreadForShutdown(PEER_EVENT_MANAGER_THREAD);
            CONFIG.saveToFile();
            synchronized (STATE) {
                STATE.set(PeerObjectLifecycleStates.TERMINATED);
            }
            synchronized (SHUTDOWN_BLOCKING_LOCK) {
                SHUTDOWN_BLOCKING_LOCK.notifyAll();
            }
        } else {
            try {
                synchronized (SHUTDOWN_BLOCKING_LOCK) {
                    SHUTDOWN_BLOCKING_LOCK.wait(30000);
                }
            } catch (InterruptedException e) {
                PeerObjectLifecycleStates finalState = STATE.get();
                synchronized (STATE) {
                    if (!finalState.equals(PeerObjectLifecycleStates.TERMINATED)) {
                        Logging.log(Level.SEVERE, "Interrupted while waiting on shutdown procedure to finish on another thread. This should never happen.");
                    } else {
                        Logging.log(Level.INFO, "Shutdown procedure has finished successfully on some other thread.");
                    }
                }
            }
        }
    }

/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ EVENT SYSTEM BEHAVIORS ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */

    /**
     * Registers handlers for events on both this peer's internal event manager AND the central event manager used by main.
     */
    private void hookEventHandlers() {
        // This event is produced by the port listening thread. should be of type IncomingConnectionEvent.
        // The code for using the socket is executed on a different thread to minimize downtime for this peer's internal event manager.
        PEER_EVENT_MANAGER.addHandler(INCOMING_CONNECTION_EVENT_NAME, event -> {
            IncomingConnectionEvent e = ((IncomingConnectionEvent) event);
            actionAcceptConnection(e.SOCK);
        });

        // This event is produced by the main thread's UI. Implementation is delegated to the performDownload() method farther down.
        PEER_EVENT_MANAGER.addHandler(
                DOWNLOAD_COMMAND_EVENT_NAME,
                event -> performDownload((DownloadCommandEvent) event)
        );

        // This event is produced by the main thread's UI. Implementation is delegated to the shutdown() method farther down.
        PEER_EVENT_MANAGER.addHandler(SHUTDOWN_COMMAND_EVENT_NAME, event -> shutdown());

        // This event is produced by the main thread's UI. Implementation is delegated to the registerResource() method farther down.
        PEER_EVENT_MANAGER.addHandler(
                RESOURCE_REGISTRATION_EVENT_NAME,
                event -> registerResource(((ResourceRegistrationEvent) event))
        );
    }

    /**
     * This is used to push events onto this peer's event queue. Used by the listening thread and by the central event manager.
     * @param e the event to process.
     */
    @Override
    public void accept(Event e) {
        PEER_EVENT_MANAGER.push(e);
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

    /**
     * Simple getter method.
     * See Main.createPeer() for how this is used.
     * Should only be used there.
     * @return A copy of the calculated names of the central event manager events for which this peer will handle via this.accept().
     */
    public String[] getCentralEventNames() {
        return Arrays.copyOf(CENTRAL_EVENT_NAMES, CENTRAL_EVENT_NAMES.length);
    }

/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */

/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ SERVER BEHAVIORS ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

    /**
     * This is the initial socket action for any and all incoming connections.
     * Call this in the handler for IncomingConnectionEvent events under the hookEvents() method.
     * @param sock Should be a newly created socket.
     */
    private void actionAcceptConnection(Socket sock) {
        // TODO: use the socket to determine what kind of request this is.

        if (true) { // TODO: replace with legitimate condition. This is just an example of what to do once the type of request has been determined.
            uploadFile(sock);
        } else if (true) { // TODO: replace with legitimate condition. This is just an example of what to do once the type of request has been determined.
            processRoutingRequest(sock);
        }

        // TODO: let remote peer know that this peer is done communicating. Don't call .close() on the socket though, that is reserved for the openSocket() method.
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
/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */



/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ CLIENT BEHAVIORS ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */

    /**
     * Helper method.
     * Creates a socket object for use in other methods.
     * @param remoteAddress the remote address of the host to connect to.
     * @param remotePort the remote port of the host to connect to. This should be the handshake port.
     * @param action A lambda to "consume" the socket. This should use the socket and properly shut it down before terminating.
     * @throws IOException if the socket could not be created.
     */
    private void openSocket(String remoteAddress, int remotePort, SocketAction action) {
        Thread socketThread = new Thread(
                () -> {
                    try (Socket socket = new Socket(remoteAddress, remotePort, null, 0)) {
                        action.useSocket(socket);

                        synchronized (SOCKET_THREAD_SET) {
                            SOCKET_THREAD_SET.remove(Thread.currentThread());
                        }
                    } catch (IOException e) {
                        Logging.log(Level.SEVERE, "Exception while using socket socket: \"" + e.getMessage()+ "\"");
                    }
                },
                CONFIG.SELF.GROUP + "." + CONFIG.SELF.NAME + "_SocketThread_remoteAddress=\"" + remoteAddress + "\"_port=" + remotePort
        );
        socketThread.start();
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
            PeerRoutingData superPeerRoutingData;
            if (CONFIG.SELF.IS_SUPER_PEER) {
                superPeerRoutingData = CONFIG.getSuperPeer(remoteClientGroup);
                if (superPeerRoutingData == null) return null;
            } else {
                superPeerRoutingData = CONFIG.LOCAL_SUPER_PEER;
            }

            Container<PeerRoutingData> response = new Container<>(null); // used to save the response from the thread socket net coode
            Container<Thread> socketThreadContainer = new Container<>(null); // used to find the thread that was created for the routing request socket, and is also used as a synchronization lock.
            openSocket(superPeerRoutingData.IP_ADDRESS, superPeerRoutingData.HANDSHAKE_PORT, socket -> {
                socketThreadContainer.set(Thread.currentThread());
                synchronized (socketThreadContainer) {
                    socketThreadContainer.notify();
                }
                /* ~~~~~~~~~~~~~~~~~~~ This is the actual net code for performing the routing table lookup request ~~~~~~~~~~~~~~~~~~~~~~ */
                // TODO: use the socket to make the request and get the response
                //BufferedWriter writer = new BufferedWriter(socket.getOutputStream());

                // NOTE: if there was some sort of error, and proper peer routing data is not received, pass null to response.set() instead of the line below.
                response.set(new PeerRoutingData("", "", "", false, -1)); // TODO: store routing request responses into here.
                /* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */
            });


            /* ~~~~~~ This section is to make sure that this method blocks until the response is received or a time limit is reached ~~~~~~~~~~ */
            try {
                // wait for the socket thread to tell us what the allocated port was
                synchronized (socketThreadContainer) {
                    socketThreadContainer.wait(); //TODO: decide on a time limit (Andrew)
                }
            } catch (InterruptedException iex) {
                if (socketThreadContainer.get() == null) { // something happened outside of what is expected of this method
                    Logging.log(Level.WARNING, "Interrupted while waiting on the port allocation for issueRoutingRequest().");
                    return null;
                } else {
                    try {
                        // we needed to get the port number from the socket thread to know which thread we need to join to wait for the response
                        socketThreadContainer.get().join(); //TODO: decide on a time limit (Andrew)
                        if (response.get() == null) {
                            return null;
                        }
                        peerRoutingData = response.get(); // this should be the response from the remote superpeer, as set in the openSocket() lambda block
                        // this will return execution to the last two lines of this method
                    } catch (InterruptedException iex2) {
                        Logging.log(Level.WARNING, "Interrupted while joining socket thread on port " + socketThreadContainer.get() + ".");
                        return null;
                    }
                }
            }
            /* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */

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
        Thread worker = new Thread(
                () -> { // issueRoutingRequest is blocking, so it's best to keep it separate from the rest of what this peer needs to do.
                        PeerRoutingData targetPeerRoutingData = issueRoutingRequest(e.REMOTE_PEER_NAME, e.REMOTE_PEER_GROUP);
                        if (targetPeerRoutingData == null) {
                            // TODO: report the failed attempt
                            return;
                        }
                        openSocket(targetPeerRoutingData.IP_ADDRESS, targetPeerRoutingData.HANDSHAKE_PORT,
                                socket -> {
                                        // TODO: use socket and the information from e to perform the download.
                                }
                        );
                        synchronized (WORKER_THREAD_SET) {
                            WORKER_THREAD_SET.remove(Thread.currentThread());
                        }
                },
                CONFIG.SELF.GROUP + "." + CONFIG.SELF.NAME + "_WorkerThread_file=\"" + e.LOCAL_FILE_DESTINATION.getName() + "\""
        );
        synchronized (WORKER_THREAD_SET) {
            WORKER_THREAD_SET.add(worker);
        }
        worker.start();
    }
/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
}
