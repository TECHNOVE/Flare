package gg.airplane.flare.util;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IntervalManager {

    private static final Logger logger = Logger.getLogger("Flare:Scheduler");
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    public void schedule(Runnable runnable, Duration interval) {
        this.exec.scheduleAtFixedRate(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                // todo if it errors out multiple times cancel?
                logger.log(Level.WARNING, "Failed to run " + runnable.getClass().getName(), t);
            }
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void cancel() {
        this.exec.shutdownNow();
    }

}
