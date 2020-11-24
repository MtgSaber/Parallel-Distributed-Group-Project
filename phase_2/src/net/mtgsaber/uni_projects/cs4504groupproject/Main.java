package net.mtgsaber.uni_projects.cs4504groupproject;

import net.mtgsaber.lib.events.AsynchronousEventManager;
import net.mtgsaber.lib.events.EventManager;
import net.mtgsaber.uni_projects.cs4504groupproject.config.Config;
import net.mtgsaber.uni_projects.cs4504groupproject.p2pclient.P2PClient;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        // initialization

        // create map between P2PClient objecst and their hosts
        final Map<String, P2PClient> p2pClientSpace = new HashMap<>();

        // set up event and thread managers
        final AsynchronousEventManager eventManager = new AsynchronousEventManager();
        final Thread eventManagerThread = new Thread(eventManager);
        eventManager.setThreadInstance(eventManagerThread);
        eventManagerThread.start();

        // create this client
//        createPeer()

        // initiate UI
        //eventManager.push(new P2PClient.FileDownloadEvent("localClientName", new File("localFileDestination"), "remotePeerName", "remotePeerGroup", "remoteResource"));
    }

    private static void createPeer(Map<String, P2PClient> p2pClientSpace, EventManager eventManager, String configFileLoc) {
        try {
            Config config = new Config(new File(configFileLoc)); // this is the line that produces the exceptions being caught.
            P2PClient client = new P2PClient(config, eventManager);
            client.start();
            p2pClientSpace.put(client.getName(), client);
            for (String eventName : client.getCentralEventNames())
                eventManager.addHandler(eventName, client);
        } catch (Exception ex) {
            // TODO: change to catch specific exceptions and do something with them.
        }
    }
}
