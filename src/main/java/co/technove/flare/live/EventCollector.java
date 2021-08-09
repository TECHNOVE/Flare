package co.technove.flare.live;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class EventCollector extends Collector {

    private final List<CollectorData> data;
    private final List<LiveEvent> events = new ArrayList<>();

    public EventCollector(CollectorData... data) {
        this.data = Arrays.asList(data);
    }

    @Override
    public List<CollectorData> getDataTypes() {
        return data;
    }

    protected void reportEvent(LiveEvent event) {
        synchronized (this.events) {
            this.events.add(event);
        }
    }

    public List<LiveEvent> getAndCopyEvents() {
        synchronized (this.events) {
            List<LiveEvent> copy = new ArrayList<>(this.events);
            this.events.clear();
            return copy;
        }
    }
}
