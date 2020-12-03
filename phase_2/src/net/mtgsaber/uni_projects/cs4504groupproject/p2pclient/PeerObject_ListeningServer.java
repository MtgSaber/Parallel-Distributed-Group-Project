package net.mtgsaber.uni_projects.cs4504groupproject.p2pclient;

import net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.events.IncomingConnectionEvent;

import java.net.Socket;

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
        // TODO: start listening on the CLIENT's handshake port.
        Socket sock = new Socket(); // create socket

        while (running) {
            // TODO: when a connection attempt occurs at the handshake port, gather whatever information the peer needs to open a connection socket.



            // TODO: pass whatever information is needed via the IncomingConnectionEvent instance. Add fields and initialize them in the constructor as necessary.
            CLIENT.accept(new IncomingConnectionEvent(CLIENT));
        }

        // TODO: stop listening for messages and release any resources in use.
    }

    public void shutdown(Thread myThread) {
        running = false;
        CLIENT = null;
        myThread.interrupt();
    }
}
