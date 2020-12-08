package net.mtgsaber.uni_projects.cs4504groupproject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mtgsaber.lib.events.AsynchronousEventManager;
import net.mtgsaber.lib.events.Event;
import net.mtgsaber.lib.events.EventManager;
import net.mtgsaber.uni_projects.cs4504groupproject.config.PeerObjectConfig;
import net.mtgsaber.uni_projects.cs4504groupproject.events.*;
import net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.Ack;
import net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.client.ConnectionRequest;
import net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.client.ResourceRequest;
import net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.client.RoutingRequest;
import net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.server.ResourceResponse;
import net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.server.RoutingResponse;
import net.mtgsaber.uni_projects.cs4504groupproject.util.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;

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

    // These are for JSON stuff in the netcode.
    private static final GsonBuilder GSON_BUILDER = new GsonBuilder();
    private static final Gson NET_GSON = GSON_BUILDER.create();
    private static final Gson LOG_GSON = GSON_BUILDER.setPrettyPrinting().create();

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
                Logging.log(Level.INFO, "Peer \"" + CONFIG.SELF.GROUP + "." + CONFIG.SELF.NAME + "\" of group \"" + CONFIG.SELF.GROUP + "\" started.");
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
            Logging.log(Level.INFO, "Beginning shutdown procedure for client \"" + CONFIG.SELF.GROUP + "." + CONFIG.SELF.NAME + "\"");
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

/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ SERVER BEHAVIORS ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */

    /**
     * This is the initial socket action for any and all incoming connections.
     * Call this in the handler for IncomingConnectionEvent events under the hookEvents() method.
     * @param sock Should be a newly accepted socket.
     */
    private void actionAcceptConnection(Socket sock) {
        Thread socketThread = new Thread(
                () -> {
                    long startTime, endTime;
                    String message;

                    Logging.log(Level.INFO, "Accepted connection from " + sock.getRemoteSocketAddress().toString() + ".");
                    Logging.log(Level.INFO, "Opening reader and writer on the socket...");
                    try ( // these are text-based and can be used to transfer string messages. I (Andrew) prefer to transfer class serializations by using NET_GSON.
                          PrintWriter sockTextWriter = new PrintWriter(sock.getOutputStream(), true);
                          BufferedReader sockTextReader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                    ) {
                        Logging.log(Level.INFO, "Successfully opened reader and writer on the socket.");

                        // use the socket to determine what kind of request this is.
                        Logging.log(Level.INFO, "Awaiting service request...");
                        startTime = System.nanoTime();
                        message = sockTextReader.readLine();
                        endTime = System.nanoTime();
                        Stats.incrementTransmissionTimeCnt(endTime - startTime);
                        Stats.incrementMessageCnt(1);
                        Stats.incrementMessageSizeCnt(message.getBytes(Charset.defaultCharset()).length);
                        ConnectionRequest connectionRequest = NET_GSON.fromJson(message, ConnectionRequest.class);
                        Logging.log(Level.INFO, "Service request received: " + LOG_GSON.toJson(connectionRequest, ConnectionRequest.class));

                        if (connectionRequest.SERVICE.equals(ConnectionRequest.Services.RESOURCE_REQUEST)) {
                            Logging.log(Level.INFO, "Service is resource request, sending ack...");
                            sockTextWriter.println(NET_GSON.toJson(new Ack(true), Ack.class));
                            Logging.log(Level.INFO, "Ack sent, calling uploadFile()...");
                            uploadFile(sock, sockTextReader, sockTextWriter);
                        } else if (connectionRequest.SERVICE.equals(ConnectionRequest.Services.ROUTING_REQUEST)) {
                            Logging.log(Level.INFO, "Service is routing request, sending ack...");
                            sockTextWriter.println(NET_GSON.toJson(new Ack(true), Ack.class));
                            Logging.log(Level.INFO, "Ack sent, calling processRoutingRequest()...");
                            processRoutingRequest(sock, sockTextReader, sockTextWriter);
                        }
                    } catch (IOException ioex) {
                        // handle the exception
                        Logging.log(Level.SEVERE, "Error Occured while attepting a connection request.");
                    }

                    // close down the socket
                    try {
                        Logging.log(Level.INFO, "Closing socket to \"" + sock.getRemoteSocketAddress().toString() + "\"...");
                        sock.close();
                        Logging.log(Level.INFO, "Socket closed.");
                    } catch (IOException ioex) {
                        // handle exception
                        Logging.log(Level.SEVERE, "Error occurred while attempting to close down the socket.");
                    } finally {
                        synchronized (SOCKET_THREAD_SET) {
                            SOCKET_THREAD_SET.remove(Thread.currentThread());
                        }
                    }
                },
                CONFIG.SELF.GROUP + "." + CONFIG.SELF.NAME + "_Server_SocketThread_socketAddress=\"" + sock.getRemoteSocketAddress().toString() + "\""
        );
        synchronized (SOCKET_THREAD_SET) {
            SOCKET_THREAD_SET.add(socketThread);
        }
        socketThread.start();
    }

    /**
     * This method uploads a file to a remote peer over the socket provided.
     * The socket has already been used to decide which action to call, and this is the action to call.
     * Obviously, this method will block until the transfer is finished, which is fine since this is executed on a dedicated socket thread.
     * @param socket the socket connected to the remote client
     * @param sockTextReader a text-based reader on the socket. used to read JSON serializations from the remote client.
     * @param sockTextWriter a text-based writer on the socket. used to write JSON serializations to the remote client.
     */
    private void uploadFile(Socket socket, BufferedReader sockTextReader, PrintWriter sockTextWriter) {
        long startTime, endTime;
        String message;

        // upload file to remote peer
        try {
            // read the request message from the remote client
            ResourceRequest request = NET_GSON.fromJson(sockTextReader.readLine(), ResourceRequest.class);
            Logging.log(Level.INFO, "Received ResourceRequest message from remote client: " + LOG_GSON.toJson(request, ResourceRequest.class));

            // request the file from the resource registry defined by the config file
            File resource = CONFIG.getResource(request.RESOURCE_NAME);
            // if the file is not registered,
            if (resource == null) {
                Logging.log(Level.INFO, "Resource not found, sending resource response...");
                // reply to the remote client that the resource isn't being shared by this peer.
                sockTextWriter.println(
                        NET_GSON.toJson(new ResourceResponse(false, -1), ResourceResponse.class)
                );
                Logging.log(Level.INFO, "Rejected resource request for non-shared resource \"" + request.RESOURCE_NAME + "\".");

            // otherwise it is registered. begin transfer procedure.
            } else {
                Logging.log(Level.INFO, "Resource found, responding with size to remote peer...");
                // reply to the remote client that their request has been accepted and give them the file size
                sockTextWriter.println(
                        NET_GSON.toJson(new ResourceResponse(true, resource.length()), ResourceResponse.class)
                );

                Logging.log(Level.INFO, "Response sent, awaiting Ack...");
                startTime = System.nanoTime();
                message = sockTextReader.readLine();
                endTime = System.nanoTime();
                Stats.incrementTransmissionTimeCnt(endTime - startTime);
                Stats.incrementMessageCnt(1);
                Stats.incrementMessageSizeCnt(message.getBytes(Charset.defaultCharset()).length);
                // wait for the client to confirm that it is ready to begin the transfer
                Ack ack = NET_GSON.fromJson(message, Ack.class);
                Logging.log(Level.INFO, "Ack received: " + LOG_GSON.toJson(ack, Ack.class));

                // if the client is ready,
                if (ack.IS_ACKNOWLEDGED) {
                    Logging.log(Level.INFO, "Client is ready, opening transfer resource...");
                    // safely open an input stream on the file
                    try (
                            FileInputStream fileInput = new FileInputStream(resource);
                            // we need a different kind of stream for byte transfers. sockTextReader & sockTextWriter are for text-based communications (i.e. JSON)
                            OutputStream outBytesStream = socket.getOutputStream()
                    ) {
                        Logging.log(Level.INFO, "Beginning non-encrypted transfer of file \"" + resource.getName() + "\"");
        /* ~~~~~~~~~~~~ gotten (and modified) from https://stackoverflow.com/questions/9520911/java-sending-and-receiving-file-byte-over-sockets ~~~~~~~~~~~~~~~~ */
                        int count;
                        byte[] buffer = new byte[4096]; // or 4096, or more
                        int countSum = 0;

                        startTime = System.nanoTime();
                        while ((count = fileInput.read(buffer)) > 0) {
                            outBytesStream.write(buffer, 0, count);
                            outBytesStream.flush();
                            countSum += count;
                        }
                        endTime = System.nanoTime();
                        Stats.incrementTransmissionTimeCnt(endTime - startTime);
                        Stats.incrementMessageCnt(1);
                        Stats.incrementBytesTransferredCnt(countSum);
        /* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */
                    } catch (FileNotFoundException fnfex) {
                        // log the error
                        Logging.log(
                                Level.SEVERE,
                                "File \"" + resource.getName() + "\" not found, but was successfully registered as resource of name \""
                                        + request.RESOURCE_NAME + "\" for client " + CONFIG.SELF.GROUP + "." + CONFIG.SELF.NAME
                                        + ". Was the file deleted during runtime?\n" + fnfex.getMessage()
                        );
                    }
                }
            }
        } catch (IOException ioex) {
            Logging.log(Level.WARNING, "Error while using socket for connection from remote client: " + ioex.getMessage());
        }
    }

    /**
     * This method uses the socket to communicate this superpeer's response to a routing table lookup request.
     * The socket has already been used to decide which action to call, and this is the action to call.
     */
    private void processRoutingRequest(Socket socket, BufferedReader sockTextReader, PrintWriter sockTextWriter) {
        long startTime, endTime;
        String message;
        try {
            startTime = System.nanoTime();
            message = sockTextReader.readLine();
            endTime = System.nanoTime();
            Stats.incrementTransmissionTimeCnt(endTime - startTime);
            Stats.incrementMessageCnt(1);
            Stats.incrementMessageSizeCnt(message.getBytes(Charset.defaultCharset()).length);
            RoutingRequest request = NET_GSON.fromJson(message, RoutingRequest.class);

            startTime = System.nanoTime();
            PeerRoutingData routingData = CONFIG.getPeer(request.TARGET_PEER_GROUP, request.TARGET_PEER_NAME);
            endTime = System.nanoTime();
            Stats.incrementRoutingTableLookupTimeCnt(endTime - startTime);
            Stats.incrementRoutingTableLookupCnt(1);
            if (routingData == null) {
                // this could block for a little bit, so make sure the socket doesn't close. whatever that entails.
                routingData = issueRoutingRequest(request.TARGET_PEER_NAME, request.TARGET_PEER_GROUP);
                if (routingData == null) {
                    // the data cannot be found. respond accordingly.
                    sockTextWriter.println(NET_GSON.toJson(new RoutingResponse(null), RoutingResponse.class));

                    return;
                }
            }

            // give the routing data to the requesting peer.
            sockTextWriter.println(NET_GSON.toJson(new RoutingResponse(routingData), RoutingResponse.class));
        } catch (IOException ioex) {
            Logging.log(Level.WARNING, "Error while reading from socket connected to: " + socket.getRemoteSocketAddress().toString() + "!");
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
     */
    private void openSocket(String remoteAddress, int remotePort, SocketAction action) {
        Thread socketThread = new Thread(
                () -> {
                    try (Socket socket = new Socket(remoteAddress, remotePort, null, 0)) {
                        Object sleepObject = new Object();
                        int retryCount = 0, retryLimit = 10000;
                        while (!socket.isConnected() && retryCount < retryLimit) {
                            if (retryCount == 0) Logging.log(Level.INFO, "Not connected yet, begin waiting...");
                            try {
                                synchronized (sleepObject) {
                                    sleepObject.wait(1);
                                    retryCount++;
                                }
                            } catch (InterruptedException ioex) {
                                Logging.log(Level.WARNING, "Interrupted while waiting for socket to connect to " + socket.getRemoteSocketAddress().toString() + "!");
                            }
                        }
                        Logging.log(Level.INFO, "Done waiting on connection, using socket now...");

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
        synchronized (SOCKET_THREAD_SET) {
            SOCKET_THREAD_SET.add(socketThread);
        }
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
     * @param targetPeerName the name of the peer whose routing data we are requesting
     * @param targetPeerGroup the name of the group that the target peer is a member of
     * @return the PeerRoutingData of the target peer, null if it could not be found.
     */
    private PeerRoutingData issueRoutingRequest(String targetPeerName, String targetPeerGroup) {
        // try the cache/routing table first
        Logging.log(Level.INFO, "Attempting to find peer " + targetPeerGroup + "." + targetPeerName + " in cache/table...");
        PeerRoutingData peerRoutingData = CONFIG.getPeer(targetPeerGroup, targetPeerName);

        // if it wasn't in the cache/routing table,
        if (peerRoutingData == null) {
            // we need the routing data for the superpeer we need to contact
            PeerRoutingData superPeerRoutingData;
            if (CONFIG.SELF.IS_SUPER_PEER) {
                Logging.log(Level.INFO, "Attempting to locate superpeer for group " + targetPeerGroup + "...");
                superPeerRoutingData = CONFIG.getSuperPeer(targetPeerGroup); // we should have the routing data for the correct superpeer
                if (superPeerRoutingData == null) {
                    Logging.log(Level.INFO, "Failed to locate superpeer for group " + targetPeerGroup + "...");
                    return null; // if we don't then we can't get the target peer's routing data.
                }
            } else {
                Logging.log(Level.INFO, "Using local superpeer routing data.");
                superPeerRoutingData = CONFIG.LOCAL_SUPER_PEER; // if we're not a superpeer then we just need to send the request to our group's superpeer
            }

            Container<PeerRoutingData> routingResponse = new Container<>(null); // used to save the routingResponse from the thread socket net coode
            Container<Thread> socketThreadContainer = new Container<>(null); // used to find the thread that was created for the routing request socket, and is also used as a synchronization lock.
            Logging.log(Level.INFO, "Calling openSocket() for routing request...");
            openSocket(superPeerRoutingData.IP_ADDRESS, superPeerRoutingData.HANDSHAKE_PORT, socket -> {
                long startTime, endTime;
                String message;

                Logging.log(Level.INFO, "Started socket thread for issueRoutingRequest().");
                // let the thread calling issueRoutingRequest() know what thread was created so it can block until we're finished here.
                socketThreadContainer.set(Thread.currentThread());
                // wake up the calling thread so they can join this one until we're done.
                synchronized (socketThreadContainer) {
                    socketThreadContainer.notify();
                }
            /* ~~~~~~~~~~~~~~~~~~~ This is the actual net code for performing the routing table lookup request ~~~~~~~~~~~~~~~~~~~~~~ */
                Logging.log(Level.INFO, "Worker thread has been notified to join this socket thread, beginning download...");
                try ( // open up text based flows for JSON object transfers
                        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
                ) {
                    Logging.log(Level.INFO, "Successfully opened reader and writer on the socket");
                    Logging.log(Level.INFO, "Sending connection request...");
                    // send an initial connection request with our desired service.
                    writer.println(NET_GSON.toJson(new ConnectionRequest(superPeerRoutingData, ConnectionRequest.Services.ROUTING_REQUEST), ConnectionRequest.class));

                    // wait for server to respond.
                    Logging.log(Level.INFO, "Awaiting Ack...");
                    startTime = System.nanoTime();
                    message = reader.readLine();
                    endTime = System.nanoTime();
                    Stats.incrementTransmissionTimeCnt(endTime - startTime);
                    Stats.incrementMessageCnt(1);
                    Stats.incrementMessageSizeCnt(message.getBytes(Charset.defaultCharset()).length);
                    Ack connectionAck = NET_GSON.fromJson(message, Ack.class);
                    Logging.log(Level.INFO, "Ack received.");

                    if (connectionAck.IS_ACKNOWLEDGED) {
                        Logging.log(Level.INFO, "Request accepted, sending Routing request...");
                        // superpeer has accepted our connection and is ready for our request
                        writer.println(NET_GSON.toJson(new RoutingRequest(targetPeerName, targetPeerGroup), RoutingRequest.class));
                        Logging.log(Level.INFO, "Routing request sent.");
                        Logging.log(Level.INFO, "Awaiting Routing response...");
                        // NOTE: usually you don't want to hold an objects monitor until you have the data you need to use its behaviors, but it's okay here since the only other thread that uses it should be joined onto this thread.
                        synchronized (routingResponse) {
                            startTime = System.nanoTime();
                            message = reader.readLine();
                            endTime = System.nanoTime();
                            Stats.incrementTransmissionTimeCnt(endTime - startTime);
                            Stats.incrementMessageCnt(1);
                            Stats.incrementMessageSizeCnt(message.getBytes(Charset.defaultCharset()).length);
                            routingResponse.set(NET_GSON.fromJson(message, RoutingResponse.class).ROUTING_DATA);
                            Logging.log(Level.INFO, "Routing response received: " + LOG_GSON.toJson(routingResponse.get(), PeerRoutingData.class));
                        }
                    } else {
                        Logging.log(Level.INFO, "Connection refused by remote superpeer " + superPeerRoutingData.GROUP + "." + superPeerRoutingData.NAME);
                    }
                }
            /* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */
            });


        /* ~~~~~~~~~~~~ This section is to make sure that this method blocks until the routingResponse is received or a time limit is reached ~~~~~~~~~~~~~~~~~ */
            try {
                // wait for the socket thread to tell us what the allocated port was
                synchronized (socketThreadContainer) {
                    Logging.log(Level.INFO, "Waiting for socket thread to be created...");
                    socketThreadContainer.wait(1000); //TODO: decide on a time limit (Andrew)
                    Logging.log(Level.INFO, "No longer waiting on socket thread.");
                    throw new InterruptedException();
                }
            } catch (InterruptedException iex) {
                if (socketThreadContainer.get() == null) { // something happened outside of what is expected of this method
                    Logging.log(Level.WARNING, "Interrupted while waiting on the port allocation for issueRoutingRequest().");
                    return null;
                } else {
                    try {
                        synchronized (socketThreadContainer) {
                            Logging.log(Level.INFO, "Joining socket thread \"" + socketThreadContainer.get());
                            // we needed to get the port number from the socket thread to know which thread we need to join to wait for the routingResponse
                            socketThreadContainer.get().join(10000); //TODO: decide on a time limit (Andrew)
                            Logging.log(Level.INFO, "No longer waiting on socket thread to terminate.");
                        }
                        if (routingResponse.get() == null) {
                            Logging.log(Level.INFO, "No routing data was found.");
                            return null;
                        }
                        peerRoutingData = routingResponse.get(); // this should be the routingResponse from the remote superpeer, as set in the openSocket() lambda block
                        // this will return execution to the last two lines of this method
                    } catch (InterruptedException iex2) {
                        Logging.log(Level.WARNING, "Interrupted while joining socket thread on port " + socketThreadContainer.get() + ".");
                        return null;
                    }
                }
            }
        /* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */
        }
        if (peerRoutingData != null) {
            Logging.log(Level.INFO, "Caching peer data: " + LOG_GSON.toJson(peerRoutingData, PeerRoutingData.class));
            CONFIG.addPeer(peerRoutingData);
        }
        Logging.log(Level.INFO, "Returning peerRoutingData=" + LOG_GSON.toJson(peerRoutingData, PeerRoutingData.class));
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
                            Logging.log(Level.SEVERE, "Cannot perform download, peer routing information is null.");
                        } else {
                            openSocket(targetPeerRoutingData.IP_ADDRESS, targetPeerRoutingData.HANDSHAKE_PORT,
                                    socket -> {
                                        long startTime, endTime;
                                        String message;
                                        Logging.log(Level.INFO, "Opening reader and writer for socket...");
                                        try ( // open up text based flows for JSON object transfers
                                              PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                                              BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
                                        ) {
                                            Logging.log(Level.INFO, "Opened resource, sending connection request...");
                                            // send an initial connection request with our desired service.
                                            writer.println(
                                                    NET_GSON.toJson(
                                                            new ConnectionRequest(
                                                                    targetPeerRoutingData,
                                                                    ConnectionRequest.Services.RESOURCE_REQUEST
                                                            ),
                                                            ConnectionRequest.class
                                                    )
                                            );

                                            // wait for server to respond.
                                            Logging.log(Level.INFO, "Request sent, awaiting ack...");
                                            startTime = System.nanoTime();
                                            message = reader.readLine();
                                            endTime = System.nanoTime();
                                            Stats.incrementTransmissionTimeCnt(endTime - startTime);
                                            Stats.incrementMessageCnt(1);
                                            Stats.incrementMessageSizeCnt(message.getBytes(Charset.defaultCharset()).length);
                                            Ack connectionAck = NET_GSON.fromJson(message, Ack.class);

                                            Logging.log(Level.INFO, "Ack received: " + LOG_GSON.toJson(connectionAck, Ack.class));
                                            if (connectionAck.IS_ACKNOWLEDGED) {
                                                Logging.log(Level.INFO, "Sending resource request...");
                                                // superpeer has accepted our connection and is ready for our request
                                                writer.println(NET_GSON.toJson(new ResourceRequest(e.REMOTE_RESOURCE), ResourceRequest.class));

                                                // get the response from the server
                                                Logging.log(Level.INFO, "request sent, awaiting response...");
                                                startTime = System.nanoTime();
                                                message = reader.readLine();
                                                endTime = System.nanoTime();
                                                Stats.incrementTransmissionTimeCnt(endTime - startTime);
                                                Stats.incrementMessageCnt(1);
                                                Stats.incrementMessageSizeCnt(message.getBytes(Charset.defaultCharset()).length);
                                                ResourceResponse response = NET_GSON.fromJson(message, ResourceResponse.class);
                                                Logging.log(Level.INFO, "response received: " + LOG_GSON.toJson(response, ResourceResponse.class));

                                                Logging.log(Level.INFO, "Sending ack...");
                                                // let server know that we're ready. ideally we would check if we have space, but due to time restrictions, we'll just have to download it anyway.
                                                writer.println(NET_GSON.toJson(new Ack(true), Ack.class));
                                                Logging.log(Level.INFO, "Ack sent.");

                                                Logging.log(Level.INFO, "Creating file..");
                                                // NOTE: there are more elegant solutions to downloading files, but due to time restrictions this will have to do.
                                                if (e.LOCAL_FILE_DESTINATION.createNewFile()) { // create the destination file
                                                    Logging.log(Level.INFO, "File created. Opening transfer resources...");
                                                    try (
                                                            // open an stream to write to this file
                                                            FileOutputStream fos = new FileOutputStream(e.LOCAL_FILE_DESTINATION);
                                                            // get a binary stream to read from the socket. local var reader is for text communications, not bytes
                                                            InputStream socketByteInput = socket.getInputStream()
                                                    ) {
                                                        Logging.log(Level.INFO, "Transfer resource opened, beginning transfer...");
                                                    /* ~~~ (modified) gotten from https://stackoverflow.com/questions/9520911/java-sending-and-receiving-file-byte-over-sockets ~~~ */
                                                        int count;
                                                        byte[] buffer = new byte[4096];
                                                        int countSum = 0;

                                                        startTime = System.nanoTime();
                                                        while ((count = socketByteInput.read(buffer)) > 0) {
                                                            fos.write(buffer, 0, count);
                                                            fos.flush();
                                                            countSum += count;
                                                        }
                                                        endTime = System.nanoTime();
                                                        Stats.incrementTransmissionTimeCnt(endTime - startTime);
                                                        Stats.incrementMessageCnt(1);
                                                        Stats.incrementBytesTransferredCnt(countSum);
                                                    /* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ */

                                                        Logging.log(Level.INFO, "Done writing to file.");
                                                    } catch (IOException ioex) {
                                                        Logging.log(
                                                                Level.SEVERE,
                                                                "Error while downloading resource \"" + e.REMOTE_RESOURCE + "\" from remote peer "
                                                                        + e.REMOTE_PEER_GROUP + "." + e.REMOTE_PEER_NAME + " to local file \""
                                                                        + e.LOCAL_FILE_DESTINATION.getName() + "\"."
                                                        );
                                                    }
                                                } else {
                                                    Logging.log(Level.SEVERE, "Could not create local file \"" + e.LOCAL_FILE_DESTINATION.getName() + "\".");
                                                }
                                            } else {
                                                Logging.log(Level.INFO, "Connection refused by remote peer " + targetPeerRoutingData.GROUP + "." + targetPeerRoutingData.NAME);
                                            }
                                        }
                                    }
                            );
                        }
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
