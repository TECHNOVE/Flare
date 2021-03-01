package gg.airplane.flare.profiling;

import gg.airplane.flare.ProfileType;
import gg.airplane.flare.ServerConnector;
import gg.airplane.flare.proto.ProfilerFileProto;
import gg.airplane.flare.proto.ProfilerFileProto.TimeProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ProfileSection {
    private final String name;
    private final ProfileType type;
    private final Map<String, ProfileSection> sections = new HashMap<>();
    private long timeTaken = 0;
    private int samples = 0;

    public ProfileSection(String name, ProfileType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public Map<String, ProfileSection> getSections() {
        return sections;
    }

    public void setTimeTakenNs(long timeTaken) {
        if (this.type != ProfileType.ALLOC) {
            timeTaken /= 1000000; // convert to ms
        }
        this.timeTaken = timeTaken;
    }

    public int getSamples() {
        return samples;
    }

    public void setSamples(int samples) {
        this.samples = samples;
    }

    public ProfileSection getSection(String name) {
        return this.sections.computeIfAbsent(name, k -> new ProfileSection(k, this.type));
    }

    public long calculateTimeTaken() {
        return this.timeTaken + this.sections.values().stream().mapToLong(ProfileSection::calculateTimeTaken).sum();
    }

    public void merge(ProfileSection section) {
        if (section == this) throw new IllegalArgumentException();
        this.timeTaken += section.timeTaken;
        this.samples += section.samples;
        for (ProfileSection value : section.getSections().values()) {
            if (this.sections.containsKey(value.getName())) {
                this.sections.get(value.getName()).merge(value);
            } else {
                this.sections.put(value.getName(), value);
            }
        }
    }

    public TimeProfile.Children toTimeChild() {
        TimeProfile.Children.Builder builder = TimeProfile.Children.newBuilder();
        builder.setName(this.name);
        builder.setTime(this.calculateTimeTaken());
        builder.setSamples(this.samples);
        String pluginForClass = ServerConnector.connector.getPluginForClass(this.name);
        if (pluginForClass != null) {
            builder.setPlugin(pluginForClass);
        }
        if (!this.sections.isEmpty()) {
            List<ProfileSection> childrenList = new ArrayList<>(this.sections.values());
            childrenList.sort((c1, c2) -> (int) (c1.calculateTimeTaken() - c2.calculateTimeTaken()));
            childrenList.stream().map(ProfileSection::toTimeChild).forEach(builder::addChildren);
        }
        return builder.build();
    }

    public ProfilerFileProto.MemoryProfile.Children toMemoryProfile() {
        ProfilerFileProto.MemoryProfile.Children.Builder builder = ProfilerFileProto.MemoryProfile.Children.newBuilder();
        builder.setName(this.name);
        builder.setBytes((int) this.calculateTimeTaken());
        String pluginForClass = ServerConnector.connector.getPluginForClass(this.name);
        if (pluginForClass != null) {
            builder.setPlugin(pluginForClass);
        }
        if (!this.sections.isEmpty()) {
            List<ProfileSection> childrenList = new ArrayList<>(this.sections.values());
            childrenList.sort((c1, c2) -> (int) (c1.calculateTimeTaken() - c2.calculateTimeTaken()));
            childrenList.stream().map(ProfileSection::toMemoryProfile).forEach(builder::addChildren);
        }
        return builder.build();
    }

    public String print(int indent) {
        String plugin = ServerConnector.connector.getPluginForClass(this.name);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            builder.append(" ");
        }
        builder.append(this.name).append(" [").append(this.calculateTimeTaken()).append("] ").append(plugin == null ? "" : plugin);
        for (ProfileSection value : this.sections.values()) {
            builder.append(value.print(indent + 1));
        }
        return builder.toString();
    }
}
