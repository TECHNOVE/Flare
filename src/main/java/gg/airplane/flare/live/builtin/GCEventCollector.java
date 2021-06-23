package gg.airplane.flare.live.builtin;

import com.google.common.collect.ImmutableMap;
import com.sun.management.GarbageCollectionNotificationInfo;
import gg.airplane.flare.Flare;
import gg.airplane.flare.live.CollectorData;
import gg.airplane.flare.live.EventCollector;
import gg.airplane.flare.live.LiveEvent;
import gg.airplane.flare.live.category.GraphCategory;
import gg.airplane.flare.live.formatter.DataFormatter;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

public class GCEventCollector extends EventCollector implements NotificationListener {

    private static final CollectorData MINOR_GC = new CollectorData("builtin:gc:minor", "Minor GC", "A small pause in the program to allow Garbage Collection to run.", DataFormatter.MILLISECONDS, GraphCategory.SYSTEM);
    private static final CollectorData MAJOR_GC = new CollectorData("builtin:gc:major", "Major GC", "A large pause in the program to allow Garbage Collection to run.", DataFormatter.MILLISECONDS, GraphCategory.SYSTEM);
    private static final CollectorData UNKNOWN_GC = new CollectorData("builtin:gc:generic", "Major GC", "A run of the Garbage Collection.", DataFormatter.MILLISECONDS, GraphCategory.SYSTEM);

    public GCEventCollector() {
        super(MINOR_GC, MAJOR_GC, UNKNOWN_GC);
    }

    private static CollectorData fromString(String string) {
        if (string.endsWith("minor GC")) {
            return MINOR_GC;
        } else if (string.endsWith("major GC")) {
            return MAJOR_GC;
        }
        return UNKNOWN_GC;
    }

    @Override
    public void start(Flare flare) {
        for (GarbageCollectorMXBean garbageCollectorBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            NotificationEmitter notificationEmitter = (NotificationEmitter) garbageCollectorBean;
            notificationEmitter.addNotificationListener(this, null, null);
        }
    }

    @Override
    public void stop(Flare flare) {
        for (GarbageCollectorMXBean garbageCollectorBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            NotificationEmitter notificationEmitter = (NotificationEmitter) garbageCollectorBean;
            try {
                notificationEmitter.removeNotificationListener(this);
            } catch (ListenerNotFoundException e) {
            }
        }
    }

    @Override
    public void handleNotification(Notification notification, Object o) {
        if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
            GarbageCollectionNotificationInfo gcInfo = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
            reportEvent(new LiveEvent(fromString(gcInfo.getGcAction()), System.currentTimeMillis(), (int) gcInfo.getGcInfo().getDuration(), ImmutableMap.of()));
        }
    }
}
