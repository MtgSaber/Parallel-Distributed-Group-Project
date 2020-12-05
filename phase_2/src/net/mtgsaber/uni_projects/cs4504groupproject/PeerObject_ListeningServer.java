package net.mtgsaber.uni_projects.cs4504groupproject;

import net.mtgsaber.uni_projects.cs4504groupproject.events.IncomingConnectionEvent;
import net.mtgsaber.uni_projects.cs4504groupproject.util.Logging;

import java.io.IOException;
import java.net.*;
import java.util.logging.Level;

/**
 * This acts as the "server" part of the peer object.
 * It simply listens on the listening port and fires events to the peer's internal event thread.
 */
public class PeerObject_ListeningServer implements Runnable {
    private PeerObject CLIENT;
    private volatile boolean running = true;

    public PeerObject_ListeningServer(PeerObject client) {
        this.CLIENT = client;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(CLIENT.getRoutingData().HANDSHAKE_PORT)) // create socket
        {
            serverSocket.setSoTimeout(1000);
            while (running) {
                // block until an incoming connection, then accept it and assing a socket
                Socket sock;
                try {
                    sock = serverSocket.accept();
                } catch (SocketTimeoutException timeoutException) {
                    if (running) continue;
                    else break;
                }

                CLIENT.accept(new IncomingConnectionEvent(sock)); // dispatch event to this client's PeerObject
            }
        } catch (IOException e) {
            Logging.log(Level.SEVERE, "Could not start listening server: IO error: " + e.toString());
        }
    }

    public void shutdown(Thread myThread) {
        running = false;
        CLIENT = null;
        myThread.interrupt();
    }
}
