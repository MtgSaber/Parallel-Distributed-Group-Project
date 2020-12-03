package components;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mtgsaber.uni_projects.cs4504groupproject.config.ConfigJSON;
import net.mtgsaber.uni_projects.cs4504groupproject.PeerRoutingData;

public class ConfigJSON_Test1 {
    public static void main(String[] args) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        PeerRoutingData[] peers = new PeerRoutingData[] {
                new PeerRoutingData("Peer1", "127.0.0.1", "Group1", true, 65000),
                new PeerRoutingData("Peer2", "127.0.0.1", "Group1", false, 65010),
                new PeerRoutingData("Peer3", "127.0.0.1", "Group1", false, 65020),
                new PeerRoutingData("Peer4", "127.0.0.1", "Group1", false, 65030),
                new PeerRoutingData("Peer5", "127.0.0.1", "Group1", false, 65040),
        };
        ConfigJSON c = new ConfigJSON(
                peers[0],
                peers[0],
                65001, 8,
                600000,
                new ConfigJSON.ResourceRegistry(
                        new String[]{"Res1", "Res2", "Res3"},
                        new String[]{
                                "./phase_2/test_res/Resource1.txt",
                                "./phase_2/test_res/Resource2.txt",
                                "./phase_2/test_res/Resource3.txt",
                        }
                ),
                new PeerRoutingData[0],
                new PeerRoutingData[]{
                        peers[1], peers[2], peers[3], peers[4]
                }
        );
        System.out.println(gson.toJson(c, c.getClass()));
    }
}
