package components;

import net.mtgsaber.lib.events.AsynchronousEventManager;
import net.mtgsaber.uni_projects.cs4504groupproject.PeerObject;
import net.mtgsaber.uni_projects.cs4504groupproject.config.PeerObjectConfig;
import net.mtgsaber.uni_projects.cs4504groupproject.util.Logging;

import java.io.File;
import java.util.logging.Level;

public class PeerObject_InstantiationTest {
    public static void main(String[] args) {
        Logging.start(System.out, true);
        Logging.setLevelStream(Level.WARNING, System.err);
        Logging.setLevelStream(Level.SEVERE, System.err);

        AsynchronousEventManager eventManager = new AsynchronousEventManager();
        Thread eventManagerThread = new Thread(
                eventManager,
                "Central Event Manager Thread"
        );
        eventManagerThread.start();

        File configFile = new File("./phase_2/test_res/Config1.json");
        PeerObject peer = null;
        try {
            PeerObjectConfig config = new PeerObjectConfig(configFile);
            peer = new PeerObject(config, eventManager);
            peer.start();
        } catch (Exception ex) {
            Logging.log(Level.SEVERE, ex.getMessage());
        } finally {
            if (peer != null) {
                Logging.log(Level.INFO, "Shutting down the test peer...");
                peer.shutdown();
                eventManager.shutdown();
            } else {
                Logging.log(Level.SEVERE, "Failed to instantiate peer!");
            }
        }
        Logging.log(Level.INFO, "finished");
    }
}
