package gg.airplane.flare.profiling.dictionary;

import gg.airplane.flare.proto.ProfilerFileProto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileDictionary {
    private final List<TypeValue> entries = new ArrayList<>();
    private final List<String> packages = new ArrayList<>();
    private int lastEntryIndex = 0;
    private int lastPackageIndex = 0;

    public synchronized int getOrAddPackage(String packageName) {
        int index = this.packages.indexOf(packageName);
        if (index < 0) {
            this.packages.add(packageName);
            index = this.packages.indexOf(packageName);
        }
        return index;
    }

    public synchronized int getOrAddMethod(TypeValue method) {
        int index = this.entries.indexOf(method);
        if (index < 0) {
            this.entries.add(method);
            index = this.entries.indexOf(method);
        }
        return index;
    }

    private synchronized List<ProfilerFileProto.MethodDictionarySlice.MethodDictionaryEntry> getNewEntries() {
        if (this.lastEntryIndex >= this.entries.size()) {
            return new ArrayList<>(0);
        }
        int index = this.lastEntryIndex;
        this.lastEntryIndex = this.entries.size();
        return this.entries.subList(index, this.entries.size()).stream()
          .map(entry -> entry.getEntry(this))
          .collect(Collectors.toList());
    }

    private synchronized List<String> getNewPackages() {
        if (this.lastPackageIndex >= this.packages.size()) {
            return new ArrayList<>(0);
        }
        int index = this.lastPackageIndex;
        this.lastPackageIndex = this.packages.size();
        return this.packages.subList(index, this.packages.size());
    }

    public synchronized ProfilerFileProto.MethodDictionarySlice toProto() {
        return ProfilerFileProto.MethodDictionarySlice.newBuilder()
          .addAllEntries(this.getNewEntries())
          .addAllPackageEntries(this.getNewPackages())
          .build();
    }
}
