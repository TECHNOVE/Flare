package gg.airplane.flare;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class ServerData {
    private final Map<String, String> files;
    private final Map<String, String> versions;

    public ServerData(Map<String, String> files, Map<String, String> versions) {
        this.files = ImmutableMap.copyOf(files);
        this.versions = ImmutableMap.copyOf(versions);
    }

    public Map<String, String> getFiles() {
        return files;
    }

    public Map<String, String> getVersions() {
        return versions;
    }
}
