package net.mtgsaber.uni_projects.cs4504groupproject.p2pclient;

/**
 * This acts as the "server" part of the p2p client.
 * It simply listens on the handshake port and fires events to the peer's main thread.
 */
public class P2PClient_ListeningServer implements Runnable {
    private final P2PClient CLIENT;
    private volatile boolean running;

    public P2PClient_ListeningServer(P2PClient client) {
        this.CLIENT = client;
    }

    @Override
    public void run() {
        running = true;

        // TODO: start listening on the CLIENT's handshake port.

        while (running) {
            // TODO: when a message is received, pass the appropriate event to CLIENT via CLIENT.accept(new Event(...))

        }

        // TODO: stop listening for messages and release any resources in use.
    }

    public void shutdown(Thread myThread) {
        running = false;
        myThread.interrupt();
    }
}
