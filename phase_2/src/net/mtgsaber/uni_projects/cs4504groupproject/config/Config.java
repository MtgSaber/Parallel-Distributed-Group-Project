package net.mtgsaber.uni_projects.cs4504groupproject.config;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.mtgsaber.uni_projects.cs4504groupproject.data.Peer;
import net.mtgsaber.uni_projects.cs4504groupproject.data.Resource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public class Config {
    private final Map<String, Resource> RES_TABLE = new HashMap<>();
    private final Set<File> REGISTERED_FILES = new HashSet<>();
    private final Map<String, Peer> PEERS = new HashMap<>();
    private final File CONFIG_FILE;
    public final long RES_MAX_USAGE_TIME;
    public final long PEER_CACHE_TIME_LIMIT;
    public final Peer LOCAL_SUPER_PEER;
    public final Peer SELF;
    public final int STARTING_PORT;
    public final int PORT_RANGE;

    private static final Gson GSON = new Gson();

    /**
     * Parses the provided file into the configuration fields of this object.
     * @param configFile the configuration file to parse.
     */
    public Config(File configFile) throws FileNotFoundException, FormatException {
        // TODO: when loading the config, add each resource entry via the addResource(name, file) method to verify their uniqueness.
        CONFIG_FILE = configFile;
        ConfigJSON json = GSON.fromJson(new FileReader(configFile), ConfigJSON.class);
        boolean resMismatch = json.RESOURCE_REGISTRY.RES_MAP_KEYS.length != json.RESOURCE_REGISTRY.RES_MAP_VALS.length;
        boolean resTimeRange = json.RES_MAX_USAGE_TIME < 1;
        boolean cacheTimeRange = json.PEER_CACHE_TIME_LIMIT < 0;
        boolean startPortRange = json.STARTING_PORT < 1025;
        boolean portRange = json.PORT_RANGE < 0;
        if (resMismatch || resTimeRange || cacheTimeRange || startPortRange || portRange)
            throw new FormatException(
                    "The following format errors were found in config file \"" + configFile.toString() + "\":"
                    + (resMismatch? "\n\tResourceRegistry: ResourceNames and ResourceFilePaths must have equal length!" : "")
                    + (resTimeRange? "\n\tResourceMaxUsageTime: Must be greater than 0" : "")
                    + (cacheTimeRange? "\n\tRoutingCacheEntryLifespan: Must be greater than -1" : "")
                    + (startPortRange? "\n\tNonHandshakePortRangeStart: Must be greater than 1024" : "")
                    + (portRange? "\n\tNonHandshakePortRangeSize: Must be greater than -1" : "")
            );

        // TODO: resource table & registered files
        for (int i = 0; i < json.RESOURCE_REGISTRY.RES_MAP_KEYS.length; i++) {
            try {
                addResource(json.RESOURCE_REGISTRY.RES_MAP_KEYS[i], new File(json.RESOURCE_REGISTRY.RES_MAP_VALS[i]));
            } catch (FileNotFoundException fnfex) {

            }
        }
        // TODO: peer table
        // TODO: primitive parameters
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
        Resource oldResource;
        synchronized (RES_TABLE) {
            if (!RES_TABLE.containsKey(name)) {
                RES_TABLE.put(name, new Resource(file));
                synchronized (REGISTERED_FILES) {
                    REGISTERED_FILES.add(file);
                }
                return true;
            }
            oldResource = RES_TABLE.get(name);
        }

        while (true) { // this is a messy approach. if issues arise from this, there are better ways to implement it.
            try {
                oldResource.getFileStream(this);
                oldResource.unRegister();
                break;
            } catch (InterruptedException e){}
        }
        synchronized (REGISTERED_FILES) {
            REGISTERED_FILES.remove(oldResource.getFile());
        }

        synchronized (RES_TABLE) {
            RES_TABLE.put(name, new Resource(file));
        }
        return true;
    }

    /**
     * Adds this peer to the peer table.
     * @param peer
     * @return
     */
    public boolean addPeer(Peer peer) {
        if (peer.getAge() > PEER_CACHE_TIME_LIMIT)
            return false;

        synchronized (PEERS) {
            PEERS.put(peer.NAME, peer);
        }
        return true;
    }

    /**
     * gets the recorded peer for the given label. if the peer has been cached for too long, it will be purged.
     * @param name
     * @return
     */
    public Peer getPeer(String name) {
        synchronized (PEERS) {
            Peer peer = PEERS.get(name);

            if (peer == null) return null;

            if (peer.getAge() >= PEER_CACHE_TIME_LIMIT && (!SELF.IS_SUPER_PEER || !SELF.GROUP.equals(peer.GROUP))) {
                PEERS.remove(name);
                return null;
            }

            return peer;
        }
    }

    /**
     * Saves the current config state to the config file.
     */
    public synchronized void saveToFile() {
        //TODO: save this config object to this.CONFIG_FILE
    }
}
