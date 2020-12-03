package components;

import net.mtgsaber.uni_projects.cs4504groupproject.config.PeerObjectConfig;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ConfigJSON_Test2 {
    public static void main(String[] args) {
        File configFile = new File("./phase_2/test_res/Config1.json");
        try {
            PeerObjectConfig config = new PeerObjectConfig(configFile);
            for (Field field : config.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.canAccess(config)) {
                    System.out.println("Field \"" + field.getName() + "\" = " + field.get(config));
                    System.out.println();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
