package gg.airplane.flare.profiling;

import gg.airplane.flare.ProfileType;
import gg.airplane.flare.ServerConnector;
import gg.airplane.flare.exceptions.UserReportableException;
import gg.airplane.flare.proto.ProfilerFileProto;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.VirtualMemory;
import oshi.software.os.OperatingSystem;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class ProfileController implements Runnable {

    private static final int switchFrequency = 20 * 30; // every 30s switch between tasks
    private static final int warmupFrequency = 20 * 10; // switch every 10s to get some initial data in the dashboard
    private static final int ticksUntilWarmedUp = 20 * 60; // do 1 minute of switching every 10s
    private static final List<ProfileType> defaultTypes = Arrays.asList(
      ProfileType.WALL,
      ProfileType.ALLOC,
      ProfileType.WALL
    ); // by default just alternate between itimer & alloc
    private static final int maxTicksToRun = 20 * 60 * 30; // run for 30 minutes before shutting off

    private int currentTaskIndex = 0;
    private int currentTick = 0;

    private final ProfilingConnection connection;
    private final ProfileType type;
    private final int interval;
    private ProfilingTask currentTask;
    private boolean running = false;

    public ProfileController(ProfileType type, int interval) throws UserReportableException {
        this.type = type;
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
          .setFormat(ProfilerFileProto.CreateProfile.Format.ONE_ZERO)
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
            // use switching
            if (this.currentTick == 0) {
                // initialize
                ProfileType type = this.type == null ? defaultTypes.get(0) : this.type;
                this.currentTask = new ProfilingTask(type, this.interval);
                this.currentTick++;
                return;
            }
            this.currentTick++;
            int freq = this.currentTick < ticksUntilWarmedUp
              ? warmupFrequency
              : switchFrequency;
            if (this.currentTick % freq == 0) {
                this.currentTaskIndex++;
                this.stop();

                ProfileType type = this.type == null ? defaultTypes.get(this.currentTaskIndex % defaultTypes.size()) : this.type;
                this.currentTask = new ProfilingTask(type, this.interval);
            }
        } catch (Throwable t) {
            ServerConnector.connector.log(Level.WARNING, "Failed to run Flare controller", t);

            // just try and kill as much as possible
            try {
                this.cancel();
            } catch (Exception e){}
            try {
                this.stop();
            } catch (Exception e){}
            try {
                AsyncProfilerIntegration.stopProfiling();
            } catch (Exception e){}
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
            ProfilerFileProto.AirplaneProfileFile file = this.currentTask.stop();
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
