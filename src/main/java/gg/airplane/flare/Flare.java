package gg.airplane.flare;

import com.google.common.collect.ImmutableSet;
import gg.airplane.flare.collectors.ThreadState;
import gg.airplane.flare.exceptions.UserReportableException;
import gg.airplane.flare.live.Collector;
import gg.airplane.flare.live.EventCollector;
import gg.airplane.flare.live.LiveCollector;
import gg.airplane.flare.live.category.GraphCategory;
import gg.airplane.flare.profiling.AsyncProfilerIntegration;
import gg.airplane.flare.profiling.InitializationException;
import gg.airplane.flare.profiling.ProfileController;
import gg.airplane.flare.proto.ProfilerFileProto;
import gg.airplane.flare.util.IntervalManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

public class Flare {

    private static boolean initialized = false;
    private final ProfilingSettings settings;
    private final ServerData serverData;
    private final FlareAuth auth;
    private final List<LiveCollector> liveCollectors = new ArrayList<>();
    private final List<EventCollector> eventCollectors = new ArrayList<>();
    private final Function<String, Optional<String>> pluginForClass;
    private final ThreadState threadState = new ThreadState();
    private final IntervalManager intervalManager = new IntervalManager();
    private final Set<GraphCategory> defaultCategories;
    private ProfileController controller;
    private boolean running = false;
    private boolean ran = false;

    public Flare(ProfilingSettings settings, ServerData serverData, FlareAuth auth, List<Collector> collectors, Function<String, Optional<String>> pluginForClass, Set<GraphCategory> defaultCategories) {
        this.settings = Objects.requireNonNull(settings);
        this.serverData = Objects.requireNonNull(serverData);
        this.auth = Objects.requireNonNull(auth);
        this.pluginForClass = pluginForClass;
        this.defaultCategories = ImmutableSet.copyOf(defaultCategories);

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

    public ProfilerFileProto.CreateProfile.HardwareInfo getHardwareInfo() {
        return ProfilerFileProto.CreateProfile.HardwareInfo.newBuilder()
                .setCpu(ProfilerFileProto.CreateProfile.HardwareInfo.CPU.newBuilder()
                        .setModel("unknown")
                        .setCoreCount(1)
                        .setThreadCount(1)
                        .setFrequency(1)
                        .build())
                .setMemory(ProfilerFileProto.CreateProfile.HardwareInfo.Memory.newBuilder()
                        .setTotal(0)
                        .setSwapTotal(0)
                        .setVirtualMax(0)
                        .build())
                .build();
    }

    public ProfilerFileProto.CreateProfile.OperatingSystem getOperatingSystem() {
        return ProfilerFileProto.CreateProfile.OperatingSystem.newBuilder()
                .setManufacturer("")
                .setFamily("")
                .setVersion("")
                .setBitness(0)
                .build();
    }

    public static List<String> initialize() throws InitializationException {
        List<String> warnings = AsyncProfilerIntegration.init();
        initialized = true;
        return warnings;
    }

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
    }

    public void stop() throws IllegalStateException {
        if (!this.running) {
            throw new IllegalStateException("This Flare is not running");
        }
        this.running = false;
        this.ran = true;
        this.intervalManager.cancel();
        this.threadState.stop();
        this.controller.end();
    }

    public ProfilingSettings getSettings() {
        return settings;
    }

    public ServerData getServerData() {
        return serverData;
    }

    public FlareAuth getAuth() {
        return auth;
    }

    public IntervalManager getIntervalManager() {
        return intervalManager;
    }

    public boolean isRan() {
        return ran;
    }

    public Optional<String> getPluginForClass(String className) {
        return this.pluginForClass == null ? Optional.empty() : this.pluginForClass.apply(className);
    }

    public ThreadState getThreadState() {
        return threadState;
    }

    public Optional<String> getURL() {
        if (this.controller == null) {
            return Optional.empty();
        }
        return Optional.of(this.auth.getUrl() + "/" + this.controller.getId());
    }

    public Set<GraphCategory> getDefaultCategories() {
        return defaultCategories;
    }
}
