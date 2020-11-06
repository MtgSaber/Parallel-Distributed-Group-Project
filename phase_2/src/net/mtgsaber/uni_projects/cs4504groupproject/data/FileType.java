package net.mtgsaber.uni_projects.cs4504groupproject.data;

import java.util.HashMap;
import java.util.Map;

public enum FileType {
    ;

    private static final Map<String, FileType> EXTENSION_LOOKUP_TABLE = new HashMap<>();
    private static final Map<String, FileType> NAME_LOOKUP_TABLE = new HashMap<>();
    private static boolean extLookupInit = false;
    private static boolean nameLookupInit = false;

    public final String NAME;
    public final String EXTENSION;

    FileType(String name, String extension) {
        this.NAME = name;
        this.EXTENSION = extension;
    }

    public static FileType getFromExtension(String extension) {
        if (extLookupInit)
            return EXTENSION_LOOKUP_TABLE.get(extension);

        for (FileType fileType : FileType.values())
            EXTENSION_LOOKUP_TABLE.put(fileType.EXTENSION, fileType);
        extLookupInit = true;
        return EXTENSION_LOOKUP_TABLE.get(extension);
    }

    public static FileType getFromName(String name) {
        if (nameLookupInit)
            return NAME_LOOKUP_TABLE.get(name);

        for (FileType fileType : FileType.values())
            NAME_LOOKUP_TABLE.put(fileType.NAME, fileType);
        nameLookupInit = true;
        return NAME_LOOKUP_TABLE.get(name);
    }
}
