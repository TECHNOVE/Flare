package gg.airplane.flare.live;

import gg.airplane.flare.Flare;

import java.util.Collection;

public abstract class Collector {

    public abstract Collection<CollectorData> getDataTypes();

    public void start(Flare flare) {
    }

    public void stop(Flare flare) {
    }
}
