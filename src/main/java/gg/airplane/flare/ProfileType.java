package gg.airplane.flare;

import one.profiler.Events;

public enum ProfileType {
    ITIMER(Events.ITIMER),
    CPU(Events.CPU),
    ALLOC(Events.ALLOC),
    LOCK(Events.LOCK),
    WALL(Events.WALL);

    private final String internalName;

    ProfileType(String internalName) {
        this.internalName = internalName;
    }

    public String getInternalName() {
        return internalName;
    }
}
