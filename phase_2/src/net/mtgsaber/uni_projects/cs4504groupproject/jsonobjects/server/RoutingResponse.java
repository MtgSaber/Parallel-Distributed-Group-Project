package net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.server;

import com.google.gson.annotations.SerializedName;
import net.mtgsaber.uni_projects.cs4504groupproject.PeerRoutingData;

public class RoutingResponse {
    @SerializedName("RoutingData")
    public final PeerRoutingData ROUTING_DATA;

    public RoutingResponse(PeerRoutingData routingData) {
        this.ROUTING_DATA = routingData;
    }
}
