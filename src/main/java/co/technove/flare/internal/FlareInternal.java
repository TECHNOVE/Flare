package co.technove.flare.internal;

import co.technove.flare.Flare;
import co.technove.flare.FlareAuth;
import co.technove.flare.FlareBuilder;
import co.technove.flare.collectors.ThreadState;
import co.technove.flare.exceptions.UserReportableException;
import co.technove.flare.internal.profiling.AsyncProfilerIntegration;
import co.technove.flare.internal.profiling.InitializationException;
import co.technove.flare.internal.profiling.ProfileController;
import co.technove.flare.internal.profiling.ProfileType;
import co.technove.flare.internal.util.IntervalManager;
import co.technove.flare.live.Collector;
import co.technove.flare.live.EventCollector;
import co.technove.flare.live.LiveCollector;
import co.technove.flare.live.category.GraphCategory;
import co.technove.flare.proto.ProfilerFileProto;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

public class FlareInternal implements Flare {

    private static boolean initialized = false;
    private final @NotNull ProfileType profileType;
    private final boolean profileMemory;
    private final @NotNull Duration interval;
    private final @NotNull Map<String, String> files;
    private final @NotNull Map<String, String> versions;
    private final @NotNull FlareAuth auth;
    private final List<LiveCollector> liveCollectors = new ArrayList<>();
    private final List<EventCollector> eventCollectors = new ArrayList<>();
    private final @Nullable Function<String, Optional<String>> pluginForClass;
    private final @NotNull ThreadState threadState = new ThreadState();
    private final @NotNull IntervalManager intervalManager = new IntervalManager();
    private final @NotNull Set<GraphCategory> defaultCategories;
    private final @Nullable FlareBuilder.HardwareBuilder hardwareBuilder;
    private final @Nullable FlareBuilder.OperatingSystemBuilder operatingSystemBuilder;
    private @Nullable ProfileController controller;
    private boolean running = false;
    private boolean ran = false;
    private Long startTime;
    private Long endTime;

    public FlareInternal(
            @NotNull ProfileType profileType,
            boolean profileMemory,
            @NotNull Duration interval,
            @NotNull Map<String, String> files,
            @NotNull Map<String, String> versions,
            @NotNull Collection<Collector> collectors,
            @NotNull FlareAuth auth,
            @Nullable Function<String, Optional<String>> pluginForClass,
            @NotNull Set<GraphCategory> defaultCategories,
            @Nullable FlareBuilder.HardwareBuilder builder,
            @Nullable FlareBuilder.OperatingSystemBuilder operatingSystemBuilder) {
        this.profileType = Objects.requireNonNull(profileType, "Profile type must be defined");
        this.profileMemory = profileMemory;
        this.interval = Objects.requireNonNull(interval, "Interval must be defined");
        this.files = files;
        this.versions = versions;
        this.auth = Objects.requireNonNull(auth, "Auth must be defined");
        this.pluginForClass = pluginForClass;
        this.defaultCategories = defaultCategories;
        this.hardwareBuilder = builder;
        this.operatingSystemBuilder = operatingSystemBuilder;

        for (Collector collector : collectors) {
            if (collector instanceof LiveCollector) {
                this.liveCollectors.add((LiveCollector) collector);
            } else if (collector instanceof EventCollector) {
                this.eventCollectors.add((EventCollector) collector);
            } else {
                throw new RuntimeException("Unknown collector type");
            }
        }
    }

    public static List<String> initialize() throws InitializationException {
        List<String> warnings = AsyncProfilerIntegration.init();
        initialized = true;
        return warnings;
    }

    public ProfilerFileProto.CreateProfile.HardwareInfo getHardwareInfo() {
        if (this.hardwareBuilder == null) {
            return ProfilerFileProto.CreateProfile.HardwareInfo.newBuilder().build();
        }
        return ProfilerFileProto.CreateProfile.HardwareInfo.newBuilder()
                .setCpu(ProfilerFileProto.CreateProfile.HardwareInfo.CPU.newBuilder()
                        .setModel(this.hardwareBuilder.getCpuModel())
                        .setCoreCount(this.hardwareBuilder.getCoreCount())
                        .setThreadCount(this.hardwareBuilder.getThreadCount())
                        .setFrequency(this.hardwareBuilder.getCpuFrequency())
                        .build())
                .setMemory(ProfilerFileProto.CreateProfile.HardwareInfo.Memory.newBuilder()
                        .setTotal(this.hardwareBuilder.getTotalMemory())
                        .setSwapTotal(this.hardwareBuilder.getTotalSwap())
                        .setVirtualMax(this.hardwareBuilder.getTotalVirtual())
                        .build())
                .build();
    }

    public ProfilerFileProto.CreateProfile.OperatingSystem getOperatingSystem() {
        if (this.operatingSystemBuilder == null) {
            return ProfilerFileProto.CreateProfile.OperatingSystem.newBuilder().build();
        }
        return ProfilerFileProto.CreateProfile.OperatingSystem.newBuilder()
                .setManufacturer(this.operatingSystemBuilder.getManufacturer())
                .setFamily(this.operatingSystemBuilder.getFamily())
                .setVersion(this.operatingSystemBuilder.getVersion())
                .setBitness(this.operatingSystemBuilder.getBitness())
                .build();
    }

    @Override
    public void start() throws IllegalStateException, UserReportableException {
        if (!initialized) {
            throw new IllegalStateException("Flare.initialize() must be called previously to starting");
        }
        if (this.ran) {
            throw new IllegalStateException("This Flare has already been ran");
        }
        this.threadState.start(this);
        // pick up threads a few times before starting
        for (int i = 0; i < 10; i++) {
            this.threadState.run();
            LockSupport.parkNanos(1000L);
        }

        this.controller = new ProfileController(this, this.liveCollectors, this.eventCollectors);
        this.running = true;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public void stop() throws IllegalStateException {
        if (!this.running) {
            throw new IllegalStateException("This Flare is not running");
        }
        this.running = false;
        this.endTime = System.currentTimeMillis();
        this.ran = true;
        this.intervalManager.cancel();
        this.threadState.stop();
        this.controller.end();
    }

    public @NotNull FlareAuth getAuth() {
        return auth;
    }

    public @NotNull ProfileType getProfileType() {
        return profileType;
    }

    public boolean isProfilingMemory() {
        return profileMemory;
    }

    public @NotNull Duration getInterval() {
        return interval;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    @Override
    public Duration getCurrentDuration() {
        if (this.startTime == null) {
            return Duration.ofMillis(0);
        }
        return Duration.ofMillis(Objects.requireNonNullElseGet(this.endTime, System::currentTimeMillis) - this.startTime);
    }

    @Override
    public Optional<URI> getURI() {
        if (this.controller == null) {
            return Optional.empty();
        }
        return Optional.of(this.auth.getUri().resolve("/" + this.controller.getId()));
    }

    public @NotNull Map<String, String> getFiles() {
        return files;
    }

    public @NotNull Map<String, String> getVersions() {
        return versions;
    }

    public @NotNull IntervalManager getIntervalManager() {
        return intervalManager;
    }

    public Optional<String> getPluginForClass(String className) {
        return this.pluginForClass == null ? Optional.empty() : this.pluginForClass.apply(className);
    }

    public @NotNull ThreadState getThreadState() {
        return threadState;
    }

    public @NotNull Set<GraphCategory> getDefaultCategories() {
        return defaultCategories;
    }
}
