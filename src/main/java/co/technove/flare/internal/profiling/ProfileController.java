package co.technove.flare.internal.profiling;

import co.technove.flare.exceptions.UserReportableException;
import co.technove.flare.internal.FlareInternal;
import co.technove.flare.internal.profiling.dictionary.ProfileDictionary;
import co.technove.flare.live.EventCollector;
import co.technove.flare.live.LiveCollector;

import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfileController implements Runnable {

    private static final Logger logger = Logger.getLogger("Flare:ProfileController");
    private final FlareInternal flare;
    private final ProfilingConnection connection;
    private final List<LiveCollector> liveCollectors;
    private final List<EventCollector> eventCollectors;
    private final ProfileDictionary dictionary = new ProfileDictionary();
    private int currentTick = 0;
    private int iterations = 0;
    private long startedAt;
    private boolean stopped = false;

    public ProfileController(FlareInternal flare, List<LiveCollector> liveCollectors, List<EventCollector> eventCollectors) throws UserReportableException {
        this.flare = flare;
        this.liveCollectors = liveCollectors;
        this.eventCollectors = eventCollectors;

        this.connection = new ProfilingConnection(flare.getAuth(), ProtoHelper.createProfile(flare, eventCollectors, liveCollectors));

        flare.getIntervalManager().schedule(this, Duration.ofMillis(50));

        this.startedAt = System.currentTimeMillis();
        for (LiveCollector liveCollector : this.liveCollectors) {
            try {
                liveCollector.start(flare);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Failed to start collector " + liveCollector.getClass().getName(), t);
            }
        }
        for (EventCollector eventCollector : this.eventCollectors) {
            try {
                eventCollector.start(flare);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Failed to start collector " + eventCollector.getClass().getName(), t);
            }
        }
    }

    public String getId() {
        return this.connection.getId();
    }

    @Override
    public synchronized void run() {
        try {
            if (this.currentTick == 0) {
                // initialize
                AsyncProfilerIntegration.startProfiling(this.flare);
            }

            if (System.currentTimeMillis() - this.startedAt > 5000) { // report every 5s
                long newStart = System.currentTimeMillis();
                this.connection.sendTimelineData(ProtoHelper.createTimeline(this.eventCollectors, this.liveCollectors, this.startedAt, newStart));
                this.startedAt = newStart;
            }

            if (this.currentTick++ >= 20L * (this.iterations < 5 ? 5 : 15)) { // 5s for first 5 iterations, 15s for rest
                this.iterations++;
                this.currentTick = 0;
                this.stop();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed to run Flare controller", t);

            // just try and kill as much as possible
            try {
                this.stop();
            } catch (Exception e) {
            }
            try {
                AsyncProfilerIntegration.stopProfiling(this.flare, this.dictionary);
            } catch (Exception e) {
            }
            this.flare.stop();
        }
    }

    public synchronized void stop() {
        AsyncProfilerIntegration.stopProfiling(this.flare, this.dictionary).ifPresent(file -> {
            try {
                this.connection.sendNewData(file.build());
            } catch (UserReportableException e) {
                logger.log(Level.WARNING, e.getUserError(), e);
            }
        });
    }

    public synchronized void end() {
        if (this.stopped) {
            return;
        }
        this.stopped = true;

        for (LiveCollector liveCollector : this.liveCollectors) {
            try {
                liveCollector.stop(flare);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Failed to start collector " + liveCollector.getClass().getName(), t);
            }
        }
        for (EventCollector eventCollector : this.eventCollectors) {
            try {
                eventCollector.stop(flare);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Failed to stop collector " + eventCollector.getClass().getName(), t);
            }
        }

        try {
            this.connection.sendTimelineData(ProtoHelper.createTimeline(this.eventCollectors, this.liveCollectors, this.startedAt, System.currentTimeMillis()));
        } catch (UserReportableException e) {
            logger.log(Level.WARNING, "Failed to send timeline data", e);
        }

        this.stop();
    }
}
