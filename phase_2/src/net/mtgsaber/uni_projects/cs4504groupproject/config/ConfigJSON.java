package net.mtgsaber.uni_projects.cs4504groupproject.config;

import com.google.gson.annotations.SerializedName;
import net.mtgsaber.uni_projects.cs4504groupproject.data.Peer;

public class ConfigJSON {
    @SerializedName("SelfParams")
    public final Peer SELF;

    @SerializedName("GroupSuperpeerInfo")
    public final Peer LOCAL_SUPER_PEER;

    @SerializedName("NonHandshakePortRangeStart")
    public final int STARTING_PORT;

    @SerializedName("NonHandshakePortRangeSize")
    public final int PORT_RANGE;

    @SerializedName("ResourceMaxUsageTime")
    public final int RES_MAX_USAGE_TIME;

    @SerializedName("RoutingCacheEntryLifespan")
    public final long PEER_CACHE_TIME_LIMIT;

    @SerializedName("ResourceRegistry")
    public final ResourceRegistry RESOURCE_REGISTRY;

    @SerializedName("SuperpeerRoutingTable")
    public final Peer[] SUPER_PEERS;

    @SerializedName("GroupRoutingTable")
    public final Peer[] GROUP_PEERS;

    public ConfigJSON(
            Peer self, Peer localSuperPeer,
            int startingPort, int portRange,
            int resMaxUsageTime, long peerCacheTimeLimit,
            ResourceRegistry resourceRegistry,
            Peer[] superPeers, Peer[] groupPeers
    ) {
        this.SELF = self;
        this.LOCAL_SUPER_PEER = localSuperPeer;
        this.STARTING_PORT = startingPort;
        this.PORT_RANGE = portRange;
        this.RES_MAX_USAGE_TIME = resMaxUsageTime;
        this.PEER_CACHE_TIME_LIMIT = peerCacheTimeLimit;
        this.RESOURCE_REGISTRY = resourceRegistry;
        this.SUPER_PEERS = superPeers;
        this.GROUP_PEERS = groupPeers;
    }

    static class ResourceRegistry {
        @SerializedName("ResourceNames")
        public final String[] RES_MAP_KEYS;
        @SerializedName("ResourceFilePaths")
        public final String[] RES_MAP_VALS;

        public ResourceRegistry(String[] resMapKeys, String[] resMapVals) {
            this.RES_MAP_KEYS = resMapKeys;
            this.RES_MAP_VALS = resMapVals;
        }
    }
