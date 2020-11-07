package net.mtgsaber.uni_projects.cs4504groupproject;

import net.mtgsaber.lib.algorithms.Pair;
import net.mtgsaber.lib.events.AsynchronousEventManager;
import net.mtgsaber.lib.events.EventManager;
import net.mtgsaber.lib.threads.Clock;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        // initialization
        final Map<String, Pair<P2PClient, Thread>> p2pClientSpace = new HashMap<>();
        final AsynchronousEventManager eventManager = new AsynchronousEventManager();
        final Clock eventManagerClock = new Clock(5, eventManager);
        final Thread eventManagerThread = new Thread(eventManagerClock);
        eventManagerClock.start();
        eventManagerThread.start();

        // start ui session
        //eventManager.push(new P2PClient.FileDownloadEvent("localClientName", new File("localFileDestination"), "remotePeerName", "remotePeerGroup", "remoteResource"));
    }

    private static void createClient(Map<String, Pair<P2PClient, Thread>> p2pClientSpace, EventManager eventManager, String configFileLoc) {
        Config config = new Config(new File(configFileLoc));
        P2PClient client = new P2PClient(config);
        Thread clientThread = new Thread(client);
        clientThread.start();
        p2pClientSpace.put(client.getName(), new Pair<>(client, clientThread));
        eventManager.addHandler(client.getName()+P2PClient.EVENT_SUFFIX, client);
    }
}
