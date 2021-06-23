package gg.airplane.flare;

import gg.airplane.flare.profiling.ProfileType;

import java.time.Duration;
import java.util.Objects;

public class ProfilingSettings {
    private final ProfileType profileType;
    private final Duration interval;

    public ProfilingSettings(ProfileType profileType, Duration interval) {
        this.profileType = Objects.requireNonNull(profileType);

        Objects.requireNonNull(interval);
        if (interval.toMillis() < 5) {
            interval = Duration.ofMillis(5);
        }
        this.interval = interval;
    }

    public ProfileType getProfileType() {
        return profileType;
    }

    public Duration getInterval() {
        return interval;
    }
}
