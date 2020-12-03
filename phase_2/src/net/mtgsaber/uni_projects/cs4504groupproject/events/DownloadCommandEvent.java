package net.mtgsaber.uni_projects.cs4504groupproject.events;

import net.mtgsaber.lib.events.Event;

import java.io.File;

public final class DownloadCommandEvent implements Event {
    public final String LOCAL_CLIENT_NAME;
    public final File LOCAL_FILE_DESTINATION;
    public final String REMOTE_PEER_NAME;
    public final String REMOTE_PEER_GROUP;
    public final String REMOTE_RESOURCE;

    public static final String SUFFIX = "_DownloadCommand";

    public DownloadCommandEvent(
            String localClientName, File localFileDestination,
            String remotePeerName, String remotePeerGroup, String remoteResource
    ) {
        this.LOCAL_CLIENT_NAME = localClientName;
        this.LOCAL_FILE_DESTINATION = localFileDestination;
        this.REMOTE_PEER_NAME = remotePeerName;
        this.REMOTE_PEER_GROUP = remotePeerGroup;
        this.REMOTE_RESOURCE = remoteResource;
    }

    @Override
    public String getName() {
        return LOCAL_CLIENT_NAME + SUFFIX;
    }
}
