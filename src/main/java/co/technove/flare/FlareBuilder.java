package co.technove.flare;

import co.technove.flare.internal.FlareInternal;
import co.technove.flare.internal.profiling.ProfileType;
import co.technove.flare.live.Collector;
import co.technove.flare.live.category.GraphCategory;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class FlareBuilder {

    private final Map<String, String> files = new HashMap<>();
    private final Map<String, String> versions = new HashMap<>();
    private final Set<Collector> collectors = new HashSet<>();
    private final Set<GraphCategory> graphCategories = new HashSet<>();
    private ProfileType profileType = ProfileType.ITIMER;
    private boolean profileMemory = false;
    private Duration interval = Duration.ofMillis(5);
    private FlareAuth auth;
    private Function<String, Optional<String>> classIdentifier;
    private HardwareBuilder hardwareBuilder;
    private OperatingSystemBuilder operatingSystemBuilder;

    public FlareBuilder() {
    }

    public FlareBuilder withProfileType(ProfileType profileType) {
        this.profileType = profileType;
        return this;
    }

    public FlareBuilder withMemoryProfiling(boolean profileMemory) {
        this.profileMemory = profileMemory;
        return this;
    }

    public FlareBuilder withInterval(Duration interval) {
        this.interval = interval;
        return this;
    }

    public FlareBuilder withAuth(FlareAuth auth) {
        this.auth = auth;
        return this;
    }

    public FlareBuilder withFile(String name, String contents) {
        this.files.put(name, contents);
        return this;
    }

    public FlareBuilder withFiles(Map<String, String> files) {
        this.files.putAll(files);
        return this;
    }

    public FlareBuilder withVersion(String software, String version) {
        this.versions.put(software, version);
        return this;
    }

    public FlareBuilder withCollectors(Collector... collectors) {
        this.collectors.addAll(Arrays.asList(collectors));
        return this;
    }

    public FlareBuilder withClassIdentifier(Function<String, Optional<String>> classIdentifier) {
        this.classIdentifier = classIdentifier;
        return this;
    }

    public FlareBuilder withGraphCategories(GraphCategory... graphCategories) {
        this.graphCategories.addAll(Arrays.asList(graphCategories));
        return this;
    }

    public FlareBuilder withHardware(HardwareBuilder builder) {
        this.hardwareBuilder = builder;
        return this;
    }

    public FlareBuilder withOperatingSystem(OperatingSystemBuilder builder) {
        this.operatingSystemBuilder = builder;
        return this;
    }

    public Flare build() {
        return new FlareInternal(
                this.profileType,
                this.profileMemory,
                this.interval,
                this.files,
                this.versions,
                this.collectors,
                this.auth,
                this.classIdentifier,
                this.graphCategories,
                this.hardwareBuilder,
                this.operatingSystemBuilder
        );
    }

    public static class HardwareBuilder {
        private String cpuModel;
        private int coreCount;
        private int threadCount;
        private long cpuFrequency;

        private long totalMemory;
        private long totalSwap;
        private long totalVirtual;

        public HardwareBuilder() {
        }

        public String getCpuModel() {
            return cpuModel;
        }

        public HardwareBuilder setCpuModel(String cpuModel) {
            this.cpuModel = cpuModel;
            return this;
        }

        public int getCoreCount() {
            return coreCount;
        }

        public HardwareBuilder setCoreCount(int coreCount) {
            this.coreCount = coreCount;
            return this;
        }

        public int getThreadCount() {
            return threadCount;
        }

        public HardwareBuilder setThreadCount(int threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public long getCpuFrequency() {
            return cpuFrequency;
        }

        public HardwareBuilder setCpuFrequency(long cpuFrequency) {
            this.cpuFrequency = cpuFrequency;
            return this;
        }

        public long getTotalMemory() {
            return totalMemory;
        }

        public HardwareBuilder setTotalMemory(long totalMemory) {
            this.totalMemory = totalMemory;
            return this;
        }

        public long getTotalSwap() {
            return totalSwap;
        }

        public HardwareBuilder setTotalSwap(long totalSwap) {
            this.totalSwap = totalSwap;
            return this;
        }

        public long getTotalVirtual() {
            return totalVirtual;
        }

        public HardwareBuilder setTotalVirtual(long totalVirtual) {
            this.totalVirtual = totalVirtual;
            return this;
        }
    }

    public static class OperatingSystemBuilder {
        private String manufacturer;
        private String family;
        private String version;
        private int bitness;

        public OperatingSystemBuilder() {
        }

        public String getManufacturer() {
            return manufacturer;
        }

        public OperatingSystemBuilder setManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
            return this;
        }

        public String getFamily() {
            return family;
        }

        public OperatingSystemBuilder setFamily(String family) {
            this.family = family;
            return this;
        }

        public String getVersion() {
            return version;
        }

        public OperatingSystemBuilder setVersion(String version) {
            this.version = version;
            return this;
        }

        public int getBitness() {
            return bitness;
        }

        public OperatingSystemBuilder setBitness(int bitness) {
            this.bitness = bitness;
            return this;
        }
    }
}
