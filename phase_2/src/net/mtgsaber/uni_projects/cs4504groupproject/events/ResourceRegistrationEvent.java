package net.mtgsaber.uni_projects.cs4504groupproject.events;

import net.mtgsaber.lib.events.Event;

import java.io.File;

public final class ResourceRegistrationEvent implements Event {
    public final String CLIENT;
    public final File FILE;
    public final String NAME;

    public static final String SUFFIX = "_ResourceRegistration";

    public ResourceRegistrationEvent(String client, File file, String name) {
        this.CLIENT = client;
        this.FILE = file;
        this.NAME = name;
    }

    @Override
    public String getName() {
        return CLIENT+SUFFIX;
    }
}
