package gg.airplane.flare.collectors;

import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GCCollector {

    private static final int timeToKeepEvents = 60 * 1000;

    public enum GCType {
        MINOR,
        MAJOR,
        UNKNOWN;

        public static GCType fromString(String string) {
            if (string.endsWith("minor GC")) {
                return GCType.MINOR;
            } else if (string.endsWith("major GC")) {
                return GCType.MAJOR;
            }
            System.err.println("Unknown GC type " + string);
            return GCType.UNKNOWN;
        }
    }

    public static class GarbageEvent {
        private final int duration;
        private final GCType type;
        private final long time;

        private GarbageEvent(int duration, String type, long time) {
            this.duration = duration;
            this.type = GCType.fromString(type);
            this.time = time;
        }

        public int getDuration() {
            return duration;
        }

        public GCType getType() {
            return type;
        }

        public long getTime() {
            return time;
        }

        @Override
        public String toString() {
            return "GarbageEvent{" +
              "duration=" + duration +
              ", type='" + type + '\'' +
              ", time=" + time +
              '}';
        }
    }

    private static final List<GarbageEvent> events = new ArrayList<>();

    static {
        for (GarbageCollectorMXBean garbageCollectorBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            NotificationEmitter notificationEmitter = (NotificationEmitter) garbageCollectorBean;
            notificationEmitter.addNotificationListener((notification, o) -> {
                if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                    GarbageCollectionNotificationInfo gcInfo = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                    events.add(new GarbageEvent((int) gcInfo.getGcInfo().getDuration(), gcInfo.getGcAction(), System.currentTimeMillis()));
                    events.removeIf(event -> System.currentTimeMillis() - event.time > timeToKeepEvents);
                }
            }, null, null);
        }

    }

    public static List<GarbageEvent> getEventsWithin(long time, TimeUnit unit) {
        long converted = unit.toMillis(time);
        return events.stream().filter(event -> System.currentTimeMillis() - event.time < converted).collect(Collectors.toList());
    }

}
