package net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.client;

import com.google.gson.annotations.SerializedName;
import net.mtgsaber.uni_projects.cs4504groupproject.PeerRoutingData;

public class ConnectionRequest {
    public enum Services {
        ROUTING_REQUEST,
        RESOURCE_REQUEST,
        ;
    }

    @SerializedName("ServerPeerRoutingData")
    public final PeerRoutingData SERVER_PEER_ROUTING_DATA;

    @SerializedName("Service")
    public final Services SERVICE;

    public ConnectionRequest(PeerRoutingData serverPeerRoutingData, Services service) {
        this.SERVER_PEER_ROUTING_DATA = serverPeerRoutingData;
        this.SERVICE = service;
    }
}
