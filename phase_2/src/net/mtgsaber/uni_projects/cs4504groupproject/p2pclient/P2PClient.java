package net.mtgsaber.uni_projects.cs4504groupproject.p2pclient;

import net.mtgsaber.lib.algorithms.Pair;
import net.mtgsaber.lib.events.AsynchronousEventManager;
import net.mtgsaber.lib.events.Event;
import net.mtgsaber.lib.events.EventManager;
import net.mtgsaber.uni_projects.cs4504groupproject.config.Config;
import net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.events.*;
import net.mtgsaber.uni_projects.cs4504groupproject.data.Peer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class P2PClient implements Consumer<Event> {
    private final Config CONFIG;
    private final EventManager CENTRAL_EVENT_MANAGER;
    private final AsynchronousEventManager CLIENT_EVENT_MANAGER = new AsynchronousEventManager();
    private final Thread CLIENT_EVENT_MANAGER_THREAD = new Thread(CLIENT_EVENT_MANAGER);

    private final Thread HANDSHAKE_THREAD;
    private final P2PClient_ListeningServer HANDSHAKE_SERVER;

    private final Map<Integer, Pair<Thread, Socket>> SOCKETS = new HashMap<>();
    private final PriorityQueue<Integer> PORT_QUEUE = new PriorityQueue<>((o1, o2) -> o2 - o1); // This will ensure that lower ports will be at front of queue

    public final String ROUTING_REQUEST_EVENT_NAME;
    public final String TRANSFER_REQUEST_EVENT_NAME;
    public final String DOWNLOAD_COMMAND_EVENT_NAME;
    public final String SHUTDOWN_COMMAND_EVENT_NAME;
    public final String RESOURCE_REGISTRATION_EVENT_NAME;
    private final String[] CENTRAL_EVENT_NAMES;

    public P2PClient(Config config, EventManager centralEventManager) {
        this.CONFIG = config;
        this.CENTRAL_EVENT_MANAGER = centralEventManager;
        CLIENT_EVENT_MANAGER.setThreadInstance(CLIENT_EVENT_MANAGER_THREAD);

        // event names
        this.ROUTING_REQUEST_EVENT_NAME = CONFIG.SELF.NAME + RoutingRequestReceivedEvent.SUFFIX;
        this.TRANSFER_REQUEST_EVENT_NAME = CONFIG.SELF.NAME + FileTransferRequestReceivedEvent.SUFFIX;
        this.DOWNLOAD_COMMAND_EVENT_NAME = CONFIG.SELF.NAME + DownloadCommandEvent.SUFFIX;
        this.SHUTDOWN_COMMAND_EVENT_NAME = CONFIG.SELF.NAME + ShutdownEvent.SUFFIX;
        this.RESOURCE_REGISTRATION_EVENT_NAME = CONFIG.SELF.NAME + ResourceRegistrationEvent.SUFFIX;
        CENTRAL_EVENT_NAMES = new String[] {
                DOWNLOAD_COMMAND_EVENT_NAME,
                SHUTDOWN_COMMAND_EVENT_NAME,
                RESOURCE_REGISTRATION_EVENT_NAME,
        };

        hookEventHandlers();

        for (int i = config.STARTING_PORT; i < config.PORT_RANGE; i++) // fill the port queue with our range of ports
            PORT_QUEUE.add(i);

        HANDSHAKE_THREAD = new Thread( // set up thead for listening on the handshake port
                HANDSHAKE_SERVER = new P2PClient_ListeningServer(this),
                config.SELF.NAME+"_HandshakeServer"
        );
    }

    private void hookEventHandlers() {
        CLIENT_EVENT_MANAGER.addHandler(ROUTING_REQUEST_EVENT_NAME, event -> {
            // TODO: use the event and create the socket. try not to slow down the event manager too much.
//            openSocket();

        });
        CLIENT_EVENT_MANAGER.addHandler(TRANSFER_REQUEST_EVENT_NAME, event -> {
            // TODO: use the event and create the socket. try not to slow down the event manager too much.
        });
        CLIENT_EVENT_MANAGER.addHandler(
                DOWNLOAD_COMMAND_EVENT_NAME,
                event -> performDownload((DownloadCommandEvent) event)
        );
        CLIENT_EVENT_MANAGER.addHandler(SHUTDOWN_COMMAND_EVENT_NAME, event -> shutdown());
        CLIENT_EVENT_MANAGER.addHandler(
                RESOURCE_REGISTRATION_EVENT_NAME,
                event -> registerResource(((ResourceRegistrationEvent) event))
        );
    }

    public String getName() { return CONFIG.SELF.NAME; }

    private void openSocket(String address, int remotePort, SocketAction action)
            throws IOException {
        if (PORT_QUEUE.isEmpty()) throw new IOException("Client overloaded; No more ports available!");
        int localPort = PORT_QUEUE.remove();
        Socket socket = new Socket(address, remotePort, null, localPort);
        Thread socketThread = new Thread(() -> {
            action.useSocket(socket);
            synchronized (SOCKETS) {
                SOCKETS.remove(localPort);
            }
            PORT_QUEUE.add(localPort);
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
    public void accept(Event e) {
        CLIENT_EVENT_MANAGER.push(e);
    }

    public String[] getCentralEventNames() {
        return Arrays.copyOf(CENTRAL_EVENT_NAMES, CENTRAL_EVENT_NAMES.length);
    }

    private void shutdown() {
        // TODO: clean up resources for shutdown.
        CONFIG.saveToFile();
        CLIENT_EVENT_MANAGER.shutdown();
        HANDSHAKE_SERVER.shutdown(HANDSHAKE_THREAD);
    }

    public void start() {
        CLIENT_EVENT_MANAGER_THREAD.start();
        HANDSHAKE_THREAD.start();
    }

    private void actionUploadFile(Socket socket) {
        // TODO: upload file to remote client
    }

    private void actionProcessRoutingRequest(Socket socket) {
        // TODO: process the routing request that was received
    }

    private Peer issueRoutingRequest(String remoteClientName, String remoteClientGroup) {
        Peer peer = CONFIG.getPeer(remoteClientName);
        if (peer == null) {
            if (CONFIG.SELF.IS_SUPER_PEER) {
                // TODO: open a socket to send a routing request to the appropriate superpeer through
                try {
                    openSocket(null, -1, socket -> { // the address should be that of the remote group's superpeer. might need to add a map for fellow superpeers.
                        // TODO: use the socket and
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
        if (peer != null) CONFIG.addPeer(peer);
        return peer;
    }

    private void performDownload(DownloadCommandEvent e) {
        new Thread(() -> {
            Peer targetPeer = issueRoutingRequest(e.REMOTE_PEER_NAME, e.REMOTE_PEER_GROUP);
            try {
                openSocket(targetPeer.IP_ADDRESS, targetPeer.HANDSHAKE_PORT, socket -> {
                    // TODO: use socket and the information from e to perform the download.

                });
            } catch (IOException ioex) {
                // TODO: handle the exception
                // maybe push an event to the central event manager so that the UI can report the error to the user?
            }
        }).start();
    }

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
