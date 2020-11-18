package net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.events;

import net.mtgsaber.lib.events.Event;
import net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.P2PClient;
import net.mtgsaber.uni_projects.cs4504groupproject.packets.P2PTransferRequest;

public final class FileTransferRequestReceivedEvent implements Event {
    public static final String SUFFIX = "_TransferRequestReceived";

    public final P2PTransferRequest PACKET;
    private final String NAME;

    public FileTransferRequestReceivedEvent(P2PClient client, P2PTransferRequest packet) {
        this.PACKET = packet;
        this.NAME = client.getName();
    }

    @Override
    public String getName() {
        return NAME;
    }
}
