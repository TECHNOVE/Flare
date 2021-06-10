package gg.airplane.flare.profiling;

import gg.airplane.flare.ProfileType;
import gg.airplane.flare.ServerConnector;
import gg.airplane.flare.exceptions.UserReportableException;
import gg.airplane.flare.profiling.dictionary.ProfileDictionary;
import gg.airplane.flare.proto.ProfilerFileProto;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.VirtualMemory;
import oshi.software.os.OperatingSystem;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public class ProfileController implements Runnable {

    private static final int switchFrequency = 20 * 30; // every 30s switch between tasks
    private static final int maxTicksToRun = 20 * 60 * 30; // run for 30 minutes before shutting off

    private int currentTick = 0;
    private int iterations = 0;

    private final ProfilingConnection connection;
    private final ProfileType type;
    private final int interval;
    private final ProfileDictionary dictionary = new ProfileDictionary();
    private ProfilingTask currentTask;
    private boolean running = false;

    public ProfileController(ProfileType type, int interval) throws UserReportableException {
        this.type = Objects.requireNonNull(type);
        this.interval = interval;

        List<ProfilerFileProto.CreateProfile.ConfigurationFile> files = new ArrayList<>();

        ServerConnector.connector.getConfigurations().forEach((key, value) -> {
            files.add(ProfilerFileProto.CreateProfile.ConfigurationFile.newBuilder()
              .setFilename(key)
              .setContents(value)
              .build());
        });

        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();

        CentralProcessor processor = hardware.getProcessor();
        CentralProcessor.ProcessorIdentifier processorIdentifier = processor.getProcessorIdentifier();

        GlobalMemory memory = hardware.getMemory();
        VirtualMemory virtualMemory = memory.getVirtualMemory();

        OperatingSystem os = systemInfo.getOperatingSystem();

        this.connection = new ProfilingConnection(ProfilerFileProto.CreateProfile.newBuilder()
          .setFormat(ProfilerFileProto.CreateProfile.Format.TWO_ZERO)
          .setVersion(ProfilerFileProto.CreateProfile.Version.newBuilder()
            .setPrimary(ServerConnector.connector.getPrimaryVersion())
            .setApi(ServerConnector.connector.getApiVersion())
            .setMc(ServerConnector.connector.getMcVersion())
            .build())
          .addAllConfigs(files)
          .setHwinfo(ProfilerFileProto.CreateProfile.HardwareInfo.newBuilder()
            .setCpu(ProfilerFileProto.CreateProfile.HardwareInfo.CPU.newBuilder()
              .setModel(processorIdentifier.getName())
              .setCoreCount(processor.getPhysicalProcessorCount())
              .setThreadCount(processor.getLogicalProcessorCount())
              .setFrequency(processor.getMaxFreq())
              .build())
            .setMemory(ProfilerFileProto.CreateProfile.HardwareInfo.Memory.newBuilder()
              .setTotal(memory.getTotal())
              .setSwapTotal(virtualMemory.getSwapTotal())
              .setVirtualMax(virtualMemory.getVirtualMax())
              .build())
            .build())
          .setVmoptions(ProfilerFileProto.CreateProfile.VMOptions.newBuilder()
            .setVersion(System.getProperty("java.version"))
            .setVendor(System.getProperty("java.vendor"))
            .setVm(System.getProperty("java.vm.name"))
            .setRuntimeName(System.getProperty("java.runtime.name"))
            .setRuntimeVersion(System.getProperty("java.runtime.version"))
            .addAllFlags(ManagementFactory.getRuntimeMXBean().getInputArguments())
            .build())
          .setOs(ProfilerFileProto.CreateProfile.OperatingSystem.newBuilder()
            .setManufacturer(os.getManufacturer())
            .setFamily(os.getFamily())
            .setVersion(os.getVersionInfo().toString())
            .setBitness(os.getBitness())
            .build())
          .build());

        ServerConnector.connector.schedule(this, 1, 1);
        this.running = true;
    }

    public String getId() {
        return this.connection.getId();
    }

    @Override
    public synchronized void run() {
        try {
            if (this.currentTick == 0) {
                // initialize
                this.currentTask = new ProfilingTask(this.type, this.interval);
                this.currentTick++;
                return;
            }
            if (this.currentTick++ >= 20L * (++this.iterations < 3 ? 10 : 30)) { // 10s for first 3 iterations, 30s for rest
                this.currentTick = 0;
                this.stop();
            }
        } catch (Throwable t) {
            ServerConnector.connector.log(Level.WARNING, "Failed to run Flare controller", t);

            // just try and kill as much as possible
            try {
                this.cancel();
            } catch (Exception e) {
            }
            try {
                this.stop();
            } catch (Exception e) {
            }
            try {
                AsyncProfilerIntegration.stopProfiling(this.dictionary);
            } catch (Exception e) {
            }
        }
    }

    public boolean isRunning() {
        return this.running;
    }

    public synchronized void cancel() {
        if (this.running) {
            ServerConnector.connector.cancel(this);
            this.running = false;
        }
        this.stop();
    }

    public synchronized void stop() {
        if (this.currentTask != null) {
            ProfilerFileProto.AirplaneProfileFile file = this.currentTask.stop(this.dictionary);
            this.currentTask = null;
            ServerConnector.connector.runAsync(() -> {
                try {
                    this.connection.sendNewData(file);
                } catch (UserReportableException e) {
                    ServerConnector.connector.log(Level.WARNING, e.getUserError(), e);
                }
            });
        }
    }
}
