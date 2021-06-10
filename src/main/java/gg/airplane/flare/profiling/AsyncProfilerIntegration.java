package gg.airplane.flare.profiling;

import com.sun.jna.Platform;
import gg.airplane.flare.ProfileType;
import gg.airplane.flare.ServerConnector;
import gg.airplane.flare.collectors.GCCollector;
import gg.airplane.flare.collectors.ThreadState;
import gg.airplane.flare.profiling.dictionary.JavaMethod;
import gg.airplane.flare.profiling.dictionary.ProfileDictionary;
import gg.airplane.flare.profiling.dictionary.TypeValue;
import gg.airplane.flare.proto.ProfilerFileProto;
import one.jfr.Dictionary;
import one.jfr.JfrReader;
import one.jfr.StackTrace;
import one.jfr.event.Event;
import one.jfr.event.EventAggregator;
import one.profiler.AsyncProfiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AsyncProfilerIntegration {

    private static final int ALLOC_INTERVAL = 8192;

    private static boolean initialized = false;

    //    private static ProfileType currentProfile;
    private static boolean profiling = false;
    private static AsyncProfiler profiler;
    private static long startTime = 0;
    private static String disabledReason = "";
    private static Path tempdir = null;
    private static String profileFile = null;
    private static int interval;

    public static void init() {
        if (initialized) {
            return;
        }
        String path = Platform.RESOURCE_PREFIX + "/libasyncProfiler.so";

        File tmp = new File(System.getProperty("java.io.tmpdir"), "libasyncProfiler.so");
        if (tmp.exists() && !tmp.delete()) {
            ServerConnector.connector.log(Level.WARNING, "Skipping profiling support, failed to access file " + tmp);
            disabledReason = "File /tmp/libasyncProfiler.so exists, but we failed to delete it.";
            return;
        }
        try (InputStream resource = AsyncProfilerIntegration.class.getClassLoader().getResourceAsStream(path)) {
            if (resource == null) {
                ServerConnector.connector.log(Level.WARNING, "Skipping profiling support, libasyncProfiler.so resource not found at " + path);
                disabledReason = "libasyncProfiler was not found inside of the Airplane JAR.";
                return;
            }
            Files.copy(resource, tmp.toPath());
        } catch (IOException e) {
            ServerConnector.connector.log(Level.WARNING, "[Airplane] Skipping profiling support, exception occurred:", e);
            disabledReason = "An internal exception occurred, please check the log.";
            return;
        }
        if (!tmp.exists()) {
            ServerConnector.connector.log(Level.WARNING, "[Airplane] Skipping profiling support, libasyncProfiler.so not found.");
            disabledReason = "libasyncProfiler.so was not extracted properly.";
            return;
        }

        String[] required = new String[]{"-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"};
        List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments().stream().map(String::toLowerCase).collect(Collectors.toList());
        for (String s : required) {
            if (!arguments.contains(s.toLowerCase())) {
                ServerConnector.connector.log(Level.WARNING, "For optimal profiles, the following flags are missing: -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints");
                break;
            }
        }

        profiler = AsyncProfiler.getInstance(tmp.getAbsolutePath());
        ServerConnector.connector.log(Level.INFO, "Enabled async profiling support, version " + profiler.getVersion());
        initialized = true;

        ThreadState.initialize();
        GCCollector.initialize();
    }

    public static String getDisabledReason() {
        return disabledReason;
    }

    public static boolean doesNotSupportProfiling() {
        return profiler == null;
    }

    public static boolean isProfiling() {
        return profiling;
    }

    private static String execute(String command) throws IOException {
        String val = profiler.execute(command);
        System.out.println("[Airplane] " + command + " -> " + val);
        return val;
    }

    synchronized static void startProfiling(ProfileType primaryType, int interval) throws IOException {
        if (profiling) {
            throw new RuntimeException("Profiling already started.");
        }
        AsyncProfilerIntegration.interval = interval;
        Thread mainThread = ServerConnector.connector.getMainThread();

        tempdir = Files.createTempDirectory("flare");
        profileFile = tempdir.resolve("flare.jfr").toString();

        String returned = execute("start,event=" + primaryType.getInternalName() + ",alloc=" + ALLOC_INTERVAL + ",interval=" + interval + "ms,threads,filter,jstackdepth=1024,jfr,file=" + profileFile);
        profiler.addThread(mainThread);
        for (Thread activeThread : ThreadState.getActiveThreads()) {
            profiler.addThread(activeThread);
        }
        if ((!returned.contains("Started ") || !returned.contains(" profiling")) && !returned.contains("Profiling started")) {
            throw new IOException("Failed to start flare: " + returned.trim());
        }
        profiling = true;
        startTime = System.currentTimeMillis();
    }

    public static String status() {
        String status = "Failed to retrieve status, an error occurred";
        try {
            status = profiler.execute("status").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return status;
    }


    private static class FinalProfileData {
        private final Map<String, ProfileSection> threads;
        private final int samples;

        private FinalProfileData(Map<String, ProfileSection> threads, int samples) {
            this.threads = threads;
            this.samples = samples;
        }

        public Map<String, ProfileSection> getThreads() {
            return threads;
        }

        public int getSamples() {
            return samples;
        }
    }

    private static FinalProfileData getProfileData(JfrReader reader, ProfileType type) {
        Map<String, ProfileSection> threadsMap = new HashMap<>();
        Dictionary<TypeValue> methodNames = new Dictionary<>(); // method names cache

        EventAggregator agg = new EventAggregator(true, true);
        int totalSamples = 0;
        for (Event event; (event = reader.readEvent(type.getEventClass())) != null; ) {
            agg.collect(event);
            totalSamples++;
        }

        agg.forEach((event, value, samples) -> {
            StackTrace stackTrace = reader.stackTraces.get(event.stackTraceId);
            if (stackTrace == null) {
                return;
            }

            long[] methods = stackTrace.methods;
            byte[] types = stackTrace.types;

            String thread = reader.threads.get(event.tid);
            ProfileSection section = null;
            for (int i = methods.length - 1; i >= 0; i--) {
                TypeValue method = JavaMethod.getMethodName(methods[i], types[i], reader, methodNames);
                if (section == null) {
                    section = threadsMap.computeIfAbsent(thread, t -> new ProfileSection(method, type));
                } else {
                    section = section.getSection(method);
                }
            }

            if (section != null) {
                section.setSamples(Math.toIntExact(samples));
                section.setTimeTakenNs(type == ProfileType.ALLOC ? value : value * interval);
            }
        });

        reader.resetRead(); // needed to read more events later

        return new FinalProfileData(threadsMap, totalSamples);
    }

    synchronized static ProfilerFileProto.AirplaneProfileFile.Builder stopProfiling(ProfileDictionary dictionary) {
        if (!profiling) {
            throw new RuntimeException("Flare has not been started.");
        }
        try {
            profiler.stop();
        } catch (Throwable t) {
            profiling = false;
            throw t;
        }

        try (JfrReader reader = new JfrReader(profileFile)) {
            FinalProfileData cpuData = getProfileData(reader, ProfileType.WALL);
            FinalProfileData allocData = getProfileData(reader, ProfileType.ALLOC);

            return ProfilerFileProto.AirplaneProfileFile.newBuilder()
              .setInfo(ProfilerFileProto.AirplaneProfileFile.ProfileInfo.newBuilder()
                .setSamples(Math.max(cpuData.samples, allocData.samples))
                .setTimeMs(reader.durationNanos / 1000000)
                .build())
              .setData(ProfilerFileProto.AirplaneProfileFile.ProfileData.newBuilder()
                .setMemoryProfile(ProfilerFileProto.MemoryProfile.newBuilder()) // add blank profile, since we use the indiviudal fields now
              )
              .setV2(ProfilerFileProto.AirplaneProfileFile.V2Data.newBuilder()
                .addAllTimeProfile(cpuData.getThreads()
                  .entrySet()
                  .stream()
                  .map(entry -> ProfilerFileProto.TimeProfileV2.newBuilder()
                    .setThread(entry.getKey())
                    .setTime(entry.getValue().calculateTimeTaken())
                    .setSamples(cpuData.samples)
                    .addAllChildren(entry.getValue().toTimeChild(dictionary).getChildrenList())
                    .build())
                  .collect(Collectors.toList()))

                .addAllMemoryProfile(allocData.getThreads()
                  .entrySet()
                  .stream()
                  .map(entry -> ProfilerFileProto.MemoryProfileV2.newBuilder()
                    .setThread(entry.getKey())
                    .setBytes(entry.getValue().calculateTimeTaken())
                    .addAllChildren(entry.getValue().toMemoryProfile(dictionary).getChildrenList())
                    .build())
                  .collect(Collectors.toList()))

                .setDictionary(dictionary.toProto())
              );
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            profiling = false;
            try {
                for (Path path : Files.list(tempdir).collect(Collectors.toList())) {
                    Files.delete(path);
                }
                Files.delete(tempdir);
            } catch (IOException e) {
            }
        }
    }
}
