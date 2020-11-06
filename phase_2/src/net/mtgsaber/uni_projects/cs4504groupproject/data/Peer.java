package net.mtgsaber.uni_projects.cs4504groupproject.data;

public class Peer {
    public final String NAME;
    public final String IP_ADDRESS;
    public final String GROUP;
    public final boolean IS_SUPER_PEER;
    public final int HANDSHAKE_PORT;
    public final long CREATION_TIME;

    public Peer(String name, String ipAddress, String group, boolean isSuperPeer, int handshakePort) {
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
}
