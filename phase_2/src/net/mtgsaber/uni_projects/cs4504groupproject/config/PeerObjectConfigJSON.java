package net.mtgsaber.uni_projects.cs4504groupproject.config;

import com.google.gson.annotations.SerializedName;
import net.mtgsaber.uni_projects.cs4504groupproject.PeerRoutingData;

public final class PeerObjectConfigJSON {
    @SerializedName("SelfParams")
    public final PeerRoutingData SELF;

    @SerializedName("GroupSuperpeerInfo")
    public final PeerRoutingData LOCAL_SUPER_PEER;

    @SerializedName("NonHandshakePortRangeStart")
    public final int STARTING_PORT;

    @SerializedName("NonHandshakePortRangeSize")
    public final int PORT_RANGE;

    @SerializedName("RoutingCacheEntryLifespan")
    public final long PEER_CACHE_TIME_LIMIT;

    @SerializedName("ResourceRegistry")
    public final ResourceRegistry RESOURCE_REGISTRY; // list of resources this node's holding

    @SerializedName("SuperpeerRoutingTable") // table of super peers
    public final PeerRoutingData[] SUPER_PEERS;

    @SerializedName("GroupRoutingTable") // table of peers in this peer's group
    public final PeerRoutingData[] GROUP_PEERS;

    public PeerObjectConfigJSON(
            PeerRoutingData self, PeerRoutingData localSuperPeer,
            int startingPort, int portRange,
            long peerCacheTimeLimit,
            ResourceRegistry resourceRegistry,
            PeerRoutingData[] superPeers, PeerRoutingData[] groupPeers
    ) {
        this.SELF = self;
        this.LOCAL_SUPER_PEER = localSuperPeer;
        this.STARTING_PORT = startingPort;
        this.PORT_RANGE = portRange;
        this.PEER_CACHE_TIME_LIMIT = peerCacheTimeLimit;
        this.RESOURCE_REGISTRY = resourceRegistry;
        this.SUPER_PEERS = superPeers;
        this.GROUP_PEERS = groupPeers;
    }

    public static class ResourceRegistry {
        @SerializedName("ResourceNames")
        public final String[] RES_MAP_KEYS;
        @SerializedName("ResourceFilePaths")
        public final String[] RES_MAP_VALS;

        public ResourceRegistry(String[] resMapKeys, String[] resMapVals) {
            this.RES_MAP_KEYS = resMapKeys;
            this.RES_MAP_VALS = resMapVals;
        }
    }
}
