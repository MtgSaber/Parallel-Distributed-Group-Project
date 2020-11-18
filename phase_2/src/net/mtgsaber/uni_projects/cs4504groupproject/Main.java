package net.mtgsaber.uni_projects.cs4504groupproject;

import net.mtgsaber.lib.events.AsynchronousEventManager;
import net.mtgsaber.lib.events.EventManager;
import net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.P2PClient;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        // initialization
        final Map<String, P2PClient> p2pClientSpace = new HashMap<>();
        final AsynchronousEventManager eventManager = new AsynchronousEventManager();
        final Thread eventManagerThread = new Thread(eventManager);
        eventManager.setThreadInstance(eventManagerThread);
        eventManagerThread.start();

        // start ui session
        //eventManager.push(new FileDownloadEvent("localClientName", new File("localFileDestination"), "remotePeerName", "remotePeerGroup", "remoteResource"));
    }

    private static void createClient(Map<String, P2PClient> p2pClientSpace, EventManager eventManager, String configFileLoc) {
        Config config = new Config(new File(configFileLoc));
        P2PClient client = new P2PClient(config, eventManager);
        client.start();
        p2pClientSpace.put(client.getName(), client);
        for (String eventName : client.getCentralEventNames())
            eventManager.addHandler(eventName, client);
    }
}
