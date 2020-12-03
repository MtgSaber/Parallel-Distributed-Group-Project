package net.mtgsaber.uni_projects.cs4504groupproject.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mtgsaber.uni_projects.cs4504groupproject.data.PeerRoutingData;
import net.mtgsaber.uni_projects.cs4504groupproject.util.Logging;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

public class Config {
    private final Map<String, File> RES_TABLE = new HashMap<>();
    private final Set<File> REGISTERED_FILES = new HashSet<>();
    private final Map<String, PeerRoutingData> PEERS = new HashMap<>();
    private final File CONFIG_FILE;
    public final long PEER_CACHE_TIME_LIMIT;
    public final PeerRoutingData LOCAL_SUPER_PEER;
    public final PeerRoutingData SELF;
    public final int STARTING_PORT;
    public final int PORT_RANGE;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Parses the provided file into the configuration fields of this object.
     * @param configFile the configuration file to parse.
     */
    public Config(File configFile) throws FileNotFoundException, FormatException {
        // when loading the config, add each resource entry via the addResource(name, file) method to verify their uniqueness.
        CONFIG_FILE = configFile;
        ConfigJSON json = GSON.fromJson(new FileReader(configFile), ConfigJSON.class);
        boolean resMismatch = json.RESOURCE_REGISTRY.RES_MAP_KEYS.length != json.RESOURCE_REGISTRY.RES_MAP_VALS.length;
        boolean cacheTimeRange = json.PEER_CACHE_TIME_LIMIT < 0;
        boolean startPortRange = json.STARTING_PORT < 1025;
        boolean portRange = json.PORT_RANGE < 0;
        if (resMismatch || cacheTimeRange || startPortRange || portRange)
            throw new FormatException(
                    "The following format errors were found in config file \"" + configFile.toString() + "\":"
                    + (resMismatch? "\n\tResourceRegistry: ResourceNames and ResourceFilePaths must have equal length!" : "")
                    + (cacheTimeRange? "\n\tRoutingCacheEntryLifespan: Must be greater than -1" : "")
                    + (startPortRange? "\n\tNonHandshakePortRangeStart: Must be greater than 1024" : "")
                    + (portRange? "\n\tNonHandshakePortRangeSize: Must be greater than -1" : "")
            );

        // resource table & registered files
        for (int i = 0; i < json.RESOURCE_REGISTRY.RES_MAP_KEYS.length; i++) {
            try {
                addResource(json.RESOURCE_REGISTRY.RES_MAP_KEYS[i], new File(json.RESOURCE_REGISTRY.RES_MAP_VALS[i]));
            } catch (TimeoutException ex) {
                Logging.log(Level.WARNING, "TimeoutException encountered during loading of config file \"" + configFile.toString() + "\". This should not occur.");
            }
        }

        // peer table
        for (PeerRoutingData peerRoutingData : json.GROUP_PEERS) addPeer(peerRoutingData);
        for (PeerRoutingData peerRoutingData : json.SUPER_PEERS) addPeer(peerRoutingData);

        // primitive parameters
        this.SELF = json.SELF;
        this.LOCAL_SUPER_PEER = json.LOCAL_SUPER_PEER;
        this.PEER_CACHE_TIME_LIMIT = json.PEER_CACHE_TIME_LIMIT;
        this.STARTING_PORT = json.STARTING_PORT;
        this.PORT_RANGE = json.PORT_RANGE;

        saveToFile();
    }

    /**
     * adds this resource under the given label.
     * @param name the label to register the resource under.
     * @param file the file to register. must not already be registered.
     * @return
     */
    public boolean addResource(String name, File file) throws FileNotFoundException, TimeoutException {
        synchronized (REGISTERED_FILES) {
            if (REGISTERED_FILES.contains(file))
                return false;
        }
        File oldResource;
        synchronized (RES_TABLE) {
            if (!RES_TABLE.containsKey(name)) {
                RES_TABLE.put(name, file);
                synchronized (REGISTERED_FILES) {
                    REGISTERED_FILES.add(file);
                }
                return true;
            }
            oldResource = RES_TABLE.get(name);
        }

        synchronized (REGISTERED_FILES) {
            REGISTERED_FILES.remove(oldResource);
        }

        synchronized (RES_TABLE) {
            RES_TABLE.put(name, file);
        }
        return true;
    }

    /**
     * Adds this peer to the peer table.
     * @param peerRoutingData
     * @return
     */
    public boolean addPeer(PeerRoutingData peerRoutingData) {
        if (peerRoutingData.getAge() > PEER_CACHE_TIME_LIMIT)
            return false;

        synchronized (PEERS) {
            PEERS.put(peerRoutingData.NAME, peerRoutingData);
        }
        return true;
    }

    /**
     * gets the recorded peer for the given label. if the peer has been cached for too long, it will be purged.
     * @param name
     * @return
     */
    public PeerRoutingData getPeer(String name) {
        synchronized (PEERS) {
            PeerRoutingData peerRoutingData = PEERS.get(name);

            if (peerRoutingData == null) return null;

            if (peerRoutingData.getAge() >= PEER_CACHE_TIME_LIMIT && (!SELF.IS_SUPER_PEER || !SELF.GROUP.equals(peerRoutingData.GROUP))) {
                PEERS.remove(name);
                return null;
            }

            return peerRoutingData;
        }
    }

    public File getResource(String name) {
        return RES_TABLE.get(name);
    }

    /**
     * Saves the current config state to the config file.
     */
    public synchronized void saveToFile() {
        //TODO: save this config object to this.CONFIG_FILE
    }
}
