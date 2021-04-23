package gg.airplane.flare.collectors;

import gg.airplane.flare.ServerConnector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class ThreadState {

    public static void initialize() {
        ServerConnector.connector.scheduleAsync(ThreadState::run, 0L, 40L);
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
            return activeCount > 1;
        }
    }

    private static final Map<Thread, ThreadValues> activeThreads = new WeakHashMap<>();

    private synchronized static void run() {
        Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> entry : stacks.entrySet()) {
            StackTraceElement[] stack = entry.getValue();
            boolean active;
            if (stack.length > 0) {
                StackTraceElement head = stack[0];
                active = !((head.getClassName().equals("jdk.internal.misc.Unsafe") && head.getMethodName().equals("park")) ||
                  (head.getClassName().equals("java.lang.Object") && head.getMethodName().equals("wait")) ||
                  (head.getClassName().equals("java.lang.Thread") && head.getMethodName().equals("sleep")) ||
                  (head.getClassName().equals("io.netty.channel.epoll.Native") && head.getMethodName().equals("epollWait")) ||
                  (head.getClassName().equals("java.lang.ref.Reference") && head.getMethodName().equals("waitForReferencePendingList")) ||
                  (head.getClassName().equals("java.io.FileInputStream") || head.getMethodName().equals("read0")));
            } else {
                active = false;
            }

            Thread thread = entry.getKey();
            ThreadValues values = activeThreads.get(thread);
            if (values == null) {
                values = new ThreadValues();
                activeThreads.put(thread, values);
            }
            System.arraycopy(values.history, 0, values.history, 1, values.history.length - 1);
            values.history[0] = active;
        }
        activeThreads.keySet().retainAll(stacks.keySet());
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
