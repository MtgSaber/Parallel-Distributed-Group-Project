package net.mtgsaber.uni_projects.cs4504groupproject.jsonobjects;

import com.google.gson.annotations.SerializedName;

public class Ack {
    @SerializedName("Ack")
    public final boolean IS_ACKNOWLEDGED;

    public Ack(boolean isAcknowledged) {
        this.IS_ACKNOWLEDGED = isAcknowledged;
    }
}
