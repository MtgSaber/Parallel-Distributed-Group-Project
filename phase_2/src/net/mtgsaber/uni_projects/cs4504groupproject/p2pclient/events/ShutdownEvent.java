package net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.events;

import net.mtgsaber.lib.events.Event;

public final class ShutdownEvent implements Event {
    public final String CLIENT_NAME;

    public static final String SUFFIX = "_Shutdown";

    public ShutdownEvent(String clientName) {
        this.CLIENT_NAME = clientName;
    }

    @Override
    public String getName() {
        return CLIENT_NAME + SUFFIX;
    }
}
