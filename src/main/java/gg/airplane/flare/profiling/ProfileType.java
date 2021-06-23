package gg.airplane.flare.profiling;

import one.jfr.event.AllocationSample;
import one.jfr.event.ContendedLock;
import one.jfr.event.Event;
import one.jfr.event.ExecutionSample;
import one.profiler.Events;

public enum ProfileType {
    ITIMER(Events.ITIMER, ExecutionSample.class),
    CPU(Events.CPU, ExecutionSample.class),
    ALLOC(Events.ALLOC, AllocationSample.class),
    LOCK(Events.LOCK, ContendedLock.class),
    WALL(Events.WALL, ExecutionSample.class);

    private final String internalName;
    private final Class<? extends Event> eventClass;

    ProfileType(String internalName, Class<? extends Event> eventClass) {
        this.internalName = internalName;
        this.eventClass = eventClass;
    }

    public String getInternalName() {
        return internalName;
    }

    public Class<? extends Event> getEventClass() {
        return eventClass;
    }
}
