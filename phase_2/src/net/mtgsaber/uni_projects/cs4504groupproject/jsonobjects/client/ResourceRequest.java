package net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.client;

import com.google.gson.annotations.SerializedName;

public class ResourceRequest {
    @SerializedName("ResourceName")
    public final String RESOURCE_NAME;

    @SerializedName("ServerPeerName")
    public final String SERVER_PEER_NAME;

    @SerializedName("ServerPeerGroup")
    public final String SERVER_PEER_GROUP;

    public ResourceRequest(String resourceName, String serverPeerName, String serverPeerGroup) {
        this.RESOURCE_NAME = resourceName;
        this.SERVER_PEER_NAME = serverPeerName;
        this.SERVER_PEER_GROUP = serverPeerGroup;
    }
}
