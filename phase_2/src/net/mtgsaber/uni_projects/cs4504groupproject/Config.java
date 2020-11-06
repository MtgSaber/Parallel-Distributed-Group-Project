package net.mtgsaber.uni_projects.cs4504groupproject;

import net.mtgsaber.uni_projects.cs4504groupproject.data.Peer;
import net.mtgsaber.uni_projects.cs4504groupproject.data.Resource;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Config {
    private final Map<String, Resource> RES_TABLE = new HashMap<>();
    private final Map<String, Peer> PEERS = new HashMap<>();
    public final long RES_MAX_USAGE_TIME;
    public final long PEER_CACHE_TIME_LIMIT;
    public final Peer LOCAL_SUPER_PEER;
    public final Peer SELF;
    public final int STARTING_PORT;
    public final int PORT_RANGE;

    /**
     * Parses the provided file into the configuration fields of this object.
     * @param resourcesConfigFile the configuration file to parse.
     */
    public Config(File resourcesConfigFile) {
        // TODO: open up file as json or XML, parse individual properties, then parse res_table and verify all entries. Discard any discrepancies from the table and save the file.
        RES_MAX_USAGE_TIME = 0;
        PEER_CACHE_TIME_LIMIT = 0;
        LOCAL_SUPER_PEER = null;
        SELF = null;
        STARTING_PORT = 0;
        PORT_RANGE = 0;
        // TODO: remove the above default values
    }

    /**
     * adds this resource under the given label.
     * @param name
     * @param resource
     * @return
     */
    public boolean putResource(String name, Resource resource) {
        if (resource.exists())
            return false;

        synchronized (RES_TABLE) {
            RES_TABLE.put(name, resource);
        }
        return true;
    }

    /**
     * Adds this peer to the peer table.
     * @param peer
     * @return
     */
    public boolean addPeer(Peer peer) {
        if (peer.getAge() > PEER_CACHE_TIME_LIMIT)
            return false;

        synchronized (PEERS) {
            PEERS.put(peer.NAME, peer);
        }
        return true;
    }

    /**
     * gets the recorded peer for the given label. if the peer has been cached for too long, it will be purged.
     * @param name
     * @return
     */
    public Peer getPeer(String name) {
        synchronized (PEERS) {
            Peer peer = PEERS.get(name);

            if (peer == null) return null;

            if (peer.getAge() >= PEER_CACHE_TIME_LIMIT && (!SELF.IS_SUPER_PEER || !SELF.GROUP.equals(peer.GROUP))) {
                PEERS.remove(name);
                return null;
            }

            return peer;
        }
    }
}
