package net.mtgsaber.uni_projects.cs4504groupproject;

/**
 * This is a basic object to represent cached(non-superpeer) or managed(superpeer) information about a p2p client
 */
public class PeerRoutingData {
    public final String NAME;
    public final String IP_ADDRESS;
    public final String GROUP;
    public final boolean IS_SUPER_PEER;
    public final int HANDSHAKE_PORT;
    private long CREATION_TIME;

    public PeerRoutingData(String name, String ipAddress, String group, boolean isSuperPeer, int handshakePort) {
        this.NAME = name;
        this.IP_ADDRESS = ipAddress;
        this.GROUP = group;
        this.IS_SUPER_PEER = isSuperPeer;
        this.HANDSHAKE_PORT = handshakePort;
        CREATION_TIME = System.currentTimeMillis();
    }

    public long getAge() {
        return System.currentTimeMillis() - CREATION_TIME;
    }

    public void makePermanent () {
        CREATION_TIME = Long.MAX_VALUE;
    }
}
