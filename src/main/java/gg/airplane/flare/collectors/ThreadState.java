package gg.airplane.flare.collectors;

import gg.airplane.flare.Flare;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class ThreadState implements Runnable {

    private final Map<Thread, ThreadValues> activeThreads = new WeakHashMap<>();

    public void start(Flare flare) {
        // since this runs once a second, a thread is active if it doesn't sleep at least once over 64s
        flare.getIntervalManager().schedule(this, Duration.ofSeconds(1));
    }

    public synchronized void stop() {
        this.activeThreads.clear();
    }

    public synchronized void run() {
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
            values.history <<= 1;
            values.history |= active ? 1 : 0;
        }
        activeThreads.keySet().retainAll(stacks.keySet());
    }

    public synchronized Set<Thread> getActiveThreads() {
        Set<Thread> set = new HashSet<>();
        for (Map.Entry<Thread, ThreadValues> entry : activeThreads.entrySet()) {
            if (entry.getValue().isActive()) {
                set.add(entry.getKey());
            }
        }
        return set;
    }

    private static class ThreadValues {
        private long history = 0;

        public boolean isActive() {
            return this.history != 0;
        }
    }

}
