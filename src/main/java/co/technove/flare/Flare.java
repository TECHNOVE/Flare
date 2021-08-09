package co.technove.flare;

import co.technove.flare.exceptions.UserReportableException;
import co.technove.flare.internal.profiling.ProfileType;
import co.technove.flare.internal.util.IntervalManager;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

public interface Flare {
    ProfileType getProfileType();

    boolean isProfilingMemory();

    Duration getInterval();

    Duration getCurrentDuration();

    boolean isRunning();

    Optional<URI> getURI();

    IntervalManager getIntervalManager();

    void start() throws IllegalStateException, UserReportableException;

    void stop() throws IllegalStateException;
}
