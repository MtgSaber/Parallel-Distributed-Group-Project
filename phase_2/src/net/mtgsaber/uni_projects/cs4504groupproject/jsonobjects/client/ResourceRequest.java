package net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.client;

import com.google.gson.annotations.SerializedName;

public class ResourceRequest {
    @SerializedName("ResourceName")
    public final String RESOURCE_NAME;

    public ResourceRequest(String resourceName) {
        this.RESOURCE_NAME = resourceName;
    }
}
