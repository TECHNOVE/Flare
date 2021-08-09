package co.technove.flare.internal.profiling;

import co.technove.flare.internal.FlareInternal;
import co.technove.flare.internal.profiling.dictionary.JavaMethod;
import co.technove.flare.internal.profiling.dictionary.ProfileDictionary;
import co.technove.flare.internal.profiling.dictionary.TypeValue;
import co.technove.flare.proto.ProfilerFileProto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ProfileSection {
    private final FlareInternal flare;
    private final TypeValue method;
    private final Map<TypeValue, ProfileSection> sections = new HashMap<>();
    private long timeTaken = 0;
    private int samples = 0;

    public ProfileSection(FlareInternal flare, TypeValue method) {
        this.flare = flare;
        this.method = method;
    }

    public TypeValue getMethod() {
        return method;
    }

    public Map<TypeValue, ProfileSection> getSections() {
        return sections;
    }

    public void setTimeTakenNs(long timeTaken) {
        this.timeTaken = timeTaken;
    }

    public int getSamples() {
        return samples;
    }

    public void setSamples(int samples) {
        this.samples = samples;
    }

    public ProfileSection getSection(TypeValue method) {
        return this.sections.computeIfAbsent(method, k -> new ProfileSection(flare, k));
    }

    public long calculateTimeTaken() {
        return this.timeTaken + this.sections.values().stream().mapToLong(ProfileSection::calculateTimeTaken).sum();
    }

    public void merge(ProfileSection section) {
        if (section == this) throw new IllegalArgumentException();
        this.timeTaken += section.timeTaken;
        this.samples += section.samples;
        for (ProfileSection value : section.getSections().values()) {
            if (this.sections.containsKey(value.getMethod())) {
                this.sections.get(value.getMethod()).merge(value);
            } else {
                this.sections.put(value.getMethod(), value);
            }
        }
    }

    public ProfilerFileProto.TimeProfileV2.Children toTimeChild(ProfileDictionary dictionary) {
        ProfilerFileProto.TimeProfileV2.Children.Builder builder = ProfilerFileProto.TimeProfileV2.Children.newBuilder();
        builder.setName(dictionary.getOrAddMethod(this.method));
        builder.setTime(this.calculateTimeTaken());
        builder.setSamples(this.samples);

        if (this.method instanceof JavaMethod) {
            this.flare.getPluginForClass(((JavaMethod) this.method).getRawClass()).ifPresent(builder::setPlugin);
        }
        if (!this.sections.isEmpty()) {
            List<ProfileSection> childrenList = new ArrayList<>(this.sections.values());
            childrenList.sort((c1, c2) -> (int) (c1.calculateTimeTaken() - c2.calculateTimeTaken()));
            childrenList.stream().map(section -> section.toTimeChild(dictionary)).forEach(builder::addChildren);
        }
        return builder.build();
    }

    public ProfilerFileProto.MemoryProfileV2.Children toMemoryProfile(ProfileDictionary dictionary) {
        ProfilerFileProto.MemoryProfileV2.Children.Builder builder = ProfilerFileProto.MemoryProfileV2.Children.newBuilder();
        builder.setName(dictionary.getOrAddMethod(this.method));
        builder.setBytes((int) this.calculateTimeTaken());

        if (this.method instanceof JavaMethod) {
            this.flare.getPluginForClass(((JavaMethod) this.method).getRawClass()).ifPresent(builder::setPlugin);
        }
        if (!this.sections.isEmpty()) {
            List<ProfileSection> childrenList = new ArrayList<>(this.sections.values());
            childrenList.sort((c1, c2) -> (int) (c1.calculateTimeTaken() - c2.calculateTimeTaken()));
            childrenList.stream().map(section -> section.toMemoryProfile(dictionary)).forEach(builder::addChildren);
        }
        return builder.build();
    }

    public String print(int indent) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            builder.append(" ");
        }
        builder.append(this.method).append(" [").append(this.calculateTimeTaken()).append("] ").append("\n");
        for (ProfileSection value : this.sections.values()) {
            builder.append(value.print(indent + 1));
        }
        return builder.toString();
    }
}
