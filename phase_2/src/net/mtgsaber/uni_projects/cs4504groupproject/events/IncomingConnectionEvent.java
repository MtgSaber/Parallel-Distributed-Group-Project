package net.mtgsaber.uni_projects.cs4504groupproject.events;

import net.mtgsaber.lib.events.Event;
import net.mtgsaber.uni_projects.cs4504groupproject.PeerObject;

import java.net.Socket;

public final class IncomingConnectionEvent implements Event {
    public static final String NAME = "IncomingConnection";

    public final Socket SOCK;

    public IncomingConnectionEvent(Socket sock) {
        this.SOCK = sock;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
