package net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.client;

import com.google.gson.annotations.SerializedName;

public class RoutingRequest {
    @SerializedName("TargetPeerName")
    public final String TARGET_PEER_NAME;

    @SerializedName("TargetPeerGroup")
    public final String TARGET_PEER_GROUP;

    public RoutingRequest(String targetPeerName, String targetPeerGroup) {
        this.TARGET_PEER_NAME = targetPeerName;
        this.TARGET_PEER_GROUP = targetPeerGroup;
    }
}
