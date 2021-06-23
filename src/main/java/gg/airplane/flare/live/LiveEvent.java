package gg.airplane.flare.live;

import java.util.Map;

public class LiveEvent {
    private final CollectorData data;
    private final long time;
    private final long duration;
    private final Map<String, String> metadata;

    public LiveEvent(CollectorData data, long time, long duration, Map<String, String> metadata) {
        this.data = data;
        this.time = time;
        this.duration = duration;
        this.metadata = metadata;
    }

    public CollectorData getData() {
        return data;
    }

    public long getTime() {
        return time;
    }

    public long getDuration() {
        return duration;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
