package net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.events;

import net.mtgsaber.lib.events.Event;
import net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.P2PClient;
import net.mtgsaber.uni_projects.cs4504groupproject.packets.routinglookup.RoutingLookupRequest;

public final class RoutingRequestReceivedEvent implements Event {
    public static final String SUFFIX = "_RoutingRequestReceived";

    public final RoutingLookupRequest PACKET;
    private final String NAME;

    public RoutingRequestReceivedEvent(P2PClient client, RoutingLookupRequest packet) {
        this.PACKET = packet;
        this.NAME = client.getName() + SUFFIX;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
