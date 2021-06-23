package gg.airplane.flare.profiling;

import com.sun.jna.Platform;
import gg.airplane.flare.Flare;
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
import one.profiler.Feature;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AsyncProfilerIntegration {

    private static final int ALLOC_INTERVAL = 8192;

    private static boolean initialized = false;

    private static boolean profiling = false;
    private static AsyncProfiler profiler;
    private static Path tempdir = null;
    private static String profileFile = null;
    private static long interval;

    public static List<String> init() throws InitializationException {
        if (initialized) {
            throw new InitializationException("Integration has already been initialized");
        }
        String path = Platform.RESOURCE_PREFIX + "/libasyncProfiler.so";

        File tmp = new File(System.getProperty("java.io.tmpdir"), "libasyncProfiler.so");
        if (tmp.exists() && !tmp.delete()) {
            throw new InitializationException("Failed to delete file " + tmp);
        }
        try (InputStream resource = AsyncProfilerIntegration.class.getClassLoader().getResourceAsStream(path)) {
            if (resource == null) {
                throw new InitializationException("Failed to find libasyncProfiler.so inside JAR, is this operating system supported?");
            }
            Files.copy(resource, tmp.toPath());
        } catch (IOException e) {
            throw new InitializationException(e);
        }
        if (!tmp.exists()) {
            throw new InitializationException("Failed to copy out libasyncProfiler.so");
        }

        List<String> warnings = new ArrayList<>();

        String[] required = new String[]{"-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"};
        List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments().stream().map(String::toLowerCase).collect(Collectors.toList());
        for (String s : required) {
            if (!arguments.contains(s.toLowerCase())) {
                warnings.add("For optimal profiles, the following flags are missing: -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints");
                break;
            }
        }

        profiler = AsyncProfiler.getInstance(tmp.getAbsolutePath());
        initialized = true;

        if (!profiler.check(Feature.DEBUG_SYMBOLS)) {
            warnings.add("Failed to find JVM debug symbols, allocation profiling will be disabled.");
        }

        return warnings;
    }

    private static String execute(String command) throws IOException {
        String val = profiler.execute(command);
//        System.out.println("[Airplane] " + command + " -> " + val);
        return val;
    }

    synchronized static void startProfiling(Flare flare) throws IOException {
        if (profiling) {
            throw new RuntimeException("Profiling already started.");
        }
        AsyncProfilerIntegration.interval = flare.getSettings().getInterval().toMillis();

        tempdir = Files.createTempDirectory("flare");
        profileFile = tempdir.resolve("flare.jfr").toString();

        String alloc = profiler.check(Feature.DEBUG_SYMBOLS) ? "alloc=" + ALLOC_INTERVAL + "," : "";
        String returned = execute("start,event=" + flare.getSettings().getProfileType().getInternalName() + "," + alloc + "interval=" + interval + "ms,threads,filter,jstackdepth=1024,jfr,file=" + profileFile);
        for (Thread activeThread : flare.getThreadState().getActiveThreads()) {
            profiler.addThread(activeThread);
        }
        if ((!returned.contains("Started ") || !returned.contains(" profiling")) && !returned.contains("Profiling started")) {
            throw new IOException("Failed to start flare: " + returned.trim());
        }
        profiling = true;
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

    private static FinalProfileData getProfileData(Flare flare, JfrReader reader, ProfileType type) {
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
            if (thread.startsWith("[tid=")) {
                return;
            }

            ProfileSection section = null;
            for (int i = methods.length - 1; i >= 0; i--) {
                TypeValue method = JavaMethod.getMethodName(methods[i], types[i], reader, methodNames);
                if (section == null) {
                    section = threadsMap.computeIfAbsent(thread, t -> new ProfileSection(flare, method));
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

    synchronized static ProfilerFileProto.AirplaneProfileFile.Builder stopProfiling(Flare flare, ProfileDictionary dictionary) {
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
            FinalProfileData cpuData = getProfileData(flare, reader, flare.getSettings().getProfileType());
            FinalProfileData allocData = getProfileData(flare, reader, ProfileType.ALLOC);

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
}
