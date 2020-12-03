package net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.events;

import net.mtgsaber.lib.events.Event;
import net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.PeerObject;

public class IncomingConnectionEvent implements Event {
    public static final String SUFFIX = "_IncomingConnection";

    private final String NAME;

    public IncomingConnectionEvent(PeerObject peer) {
        this.NAME = peer.getName() + SUFFIX;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
