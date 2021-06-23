package gg.airplane.flare.profiling;

import gg.airplane.flare.Flare;
import gg.airplane.flare.exceptions.UserReportableException;
import gg.airplane.flare.live.EventCollector;
import gg.airplane.flare.live.LiveCollector;
import gg.airplane.flare.profiling.dictionary.ProfileDictionary;
import gg.airplane.flare.proto.ProfilerFileProto;

import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProfileController implements Runnable {

    private static final Logger logger = Logger.getLogger("Flare:ProfileController");
    private final Flare flare;
    private final ProfilingConnection connection;
    private final List<LiveCollector> liveCollectors;
    private final List<EventCollector> eventCollectors;
    private final ProfileDictionary dictionary = new ProfileDictionary();
    private int currentTick = 0;
    private int iterations = 0;
    private long startedAt;
    private boolean stopped = false;

    public ProfileController(Flare flare, List<LiveCollector> liveCollectors, List<EventCollector> eventCollectors) throws UserReportableException {
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

            if ((this.iterations < 5 && this.currentTick % 20 == 0) || this.currentTick % (20 * 5) == 0) { // every second send timeline data, otherwise 5s
                this.connection.sendTimelineData(ProtoHelper.createTimeline(this.eventCollectors, this.liveCollectors, this.startedAt, System.currentTimeMillis()));
                this.startedAt = System.currentTimeMillis();
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
        ProfilerFileProto.AirplaneProfileFile.Builder file = AsyncProfilerIntegration.stopProfiling(this.flare, this.dictionary);
        try {
            this.connection.sendNewData(file.build());
        } catch (UserReportableException e) {
            logger.log(Level.WARNING, e.getUserError(), e);
        }
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

        this.stop();
    }
}
