package net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects.server;

import com.google.gson.annotations.SerializedName;

public class ResourceResponse {
    @SerializedName("IsAccepted")
    public final boolean IS_ACCEPTED;

    @SerializedName("FileSize")
    public final long FILE_SIZE;

    public ResourceResponse(boolean isAccepted, long fileSize) {
        this.IS_ACCEPTED = isAccepted;
        this.FILE_SIZE = fileSize;
    }
}
