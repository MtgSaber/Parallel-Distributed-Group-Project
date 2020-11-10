package net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.events;

import net.mtgsaber.lib.events.Event;
import net.mtgsaber.uni_projects.cs4504groupproject.data.FileType;

import java.io.File;

public final class ResourceRegistrationEvent implements Event {
    public final String CLIENT;
    public final File FILE;
    public final String NAME;
    public final FileType TYPE;

    public static final String SUFFIX = "_ResourceRegistration";

    public ResourceRegistrationEvent(String client, File file, String name, FileType type) {
        this.CLIENT = client;
        this.FILE = file;
        this.NAME = name;
        this.TYPE = type;
    }

    @Override
    public String getName() {
        return CLIENT+SUFFIX;
    }
}
