package gg.airplane.flare.collectors;

import gg.airplane.flare.ServerConnector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class ThreadState {

    public static void initialize() {
        ServerConnector.connector.scheduleAsync(ThreadState::run, 20L, 20L);
    }

    private static class ThreadValues {
        private final boolean[] history = new boolean[10];

        public boolean isActive() {
            int activeCount = 0;
            for (boolean active : this.history) {
                if (active) {
                    activeCount++;
                }
            }
            return activeCount > 3;
        }
    }

    private static final Map<Thread, ThreadValues> activeThreads = new WeakHashMap<>();

    private synchronized static void run() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            ThreadValues values = activeThreads.get(thread);
            if (values == null) {
                values = new ThreadValues();
                activeThreads.put(thread, values);
            }
            System.arraycopy(values.history, 0, values.history, 1, values.history.length - 1);
            values.history[0] = thread.getState() == Thread.State.RUNNABLE;
        }
    }

    public synchronized static Set<Thread> getActiveThreads() {
        Set<Thread> set = new HashSet<>();
        for (Map.Entry<Thread, ThreadValues> entry : activeThreads.entrySet()) {
            if (entry.getValue().isActive()) {
                set.add(entry.getKey());
            }
        }
        return set;
    }

}
