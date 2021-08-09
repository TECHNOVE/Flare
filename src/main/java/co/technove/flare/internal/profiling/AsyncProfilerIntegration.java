package co.technove.flare.internal.profiling;

import co.technove.flare.internal.FlareInternal;
import co.technove.flare.internal.profiling.dictionary.JavaMethod;
import co.technove.flare.internal.profiling.dictionary.ProfileDictionary;
import co.technove.flare.internal.profiling.dictionary.TypeValue;
import co.technove.flare.proto.ProfilerFileProto;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Linux")) {
            osName = "linux";
        } else {
            throw new InitializationException("Flare does not support the operating system " + osName);
        }
        String path = osName + "-" + System.getProperty("os.arch") + "/libasyncProfiler.so";

        File tmp = new File(System.getProperty("java.io.tmpdir"), "libasyncProfiler.so");
        if (tmp.exists() && !tmp.delete()) {
            throw new InitializationException("Failed to delete file " + tmp);
        }
        try (InputStream resource = AsyncProfilerIntegration.class.getClassLoader().getResourceAsStream(path)) {
            if (resource == null) {
                throw new InitializationException("Failed to find " + path + " inside JAR, is this operating system supported?");
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

        boolean supportsProfilingMemory = false;
        try {
            supportsProfilingMemory = profiler.execute("check,alloc").trim().equals("OK");
        } catch (IOException | IllegalStateException e) {
        }

        if (!supportsProfilingMemory) {
            warnings.add("Failed to find JVM debug symbols, allocation profiling will be disabled.");
        }

        return warnings;
    }

    private static String execute(String command) throws IOException {
        String val = profiler.execute(command);
//        System.out.println("[Airplane] " + command + " -> " + val);
        return val;
    }

    synchronized static void startProfiling(FlareInternal flare) throws IOException {
        if (profiling) {
            throw new RuntimeException("Profiling already started.");
        }
        AsyncProfilerIntegration.interval = flare.getInterval().toMillis();

        tempdir = Files.createTempDirectory("flare");
        profileFile = tempdir.resolve("flare.jfr").toString();

        boolean supportsProfilingMemory = false;
        try {
            supportsProfilingMemory = profiler.execute("check,alloc").trim().equals("OK");
        } catch (IOException | IllegalStateException e) {
        }

        String alloc = supportsProfilingMemory && flare.isProfilingMemory() ? "alloc=" + ALLOC_INTERVAL + "," : "";
        String returned = execute("start,event=" + flare.getProfileType().getInternalName() + "," + alloc + "interval=" + interval + "ms,threads,filter,jstackdepth=1024,jfr,file=" + profileFile);
        for (Thread activeThread : flare.getThreadState().getActiveThreads()) {
            profiler.addThread(activeThread);
        }
        if ((!returned.contains("Started ") || !returned.contains(" profiling")) && !returned.contains("Profiling started")) {
            throw new IOException("Failed to start flare: " + returned.trim());
        }
        profiling = true;
    }

    private static FinalProfileData getProfileData(FlareInternal flare, JfrReader reader, ProfileType type) {
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

    synchronized static Optional<ProfilerFileProto.AirplaneProfileFile.Builder> stopProfiling(FlareInternal flare, ProfileDictionary dictionary) {
        if (!profiling) {
            return Optional.empty();
        }
        try {
            profiler.stop();
        } catch (Throwable t) {
            profiling = false;
            throw t;
        }

        try (JfrReader reader = new JfrReader(profileFile)) {
            FinalProfileData cpuData = getProfileData(flare, reader, flare.getProfileType());
            FinalProfileData allocData = getProfileData(flare, reader, ProfileType.ALLOC);

            return Optional.of(ProfilerFileProto.AirplaneProfileFile.newBuilder()
                    .setInfo(ProfilerFileProto.AirplaneProfileFile.ProfileInfo.newBuilder()
                            .setSamples(Math.max(cpuData.samples, allocData.samples))
                            .setTimeMs(reader.durationNanos / 1000000)
                            .build())
                    .setData(ProfilerFileProto.AirplaneProfileFile.ProfileData.newBuilder()
                            .setMemoryProfile(ProfilerFileProto.MemoryProfile.newBuilder()) // add blank profile, since we use the indiviudal fields now
                    )
                    .setV2(ProfilerFileProto.AirplaneProfileFile.V2Data.newBuilder()
                            .addAllTimeProfile(cpuData.threads
                                    .entrySet()
                                    .stream()
                                    .map(entry -> ProfilerFileProto.TimeProfileV2.newBuilder()
                                            .setThread(entry.getKey())
                                            .setTime(entry.getValue().calculateTimeTaken())
                                            .setSamples(cpuData.samples)
                                            .addAllChildren(entry.getValue().toTimeChild(dictionary).getChildrenList())
                                            .build())
                                    .collect(Collectors.toList()))

                            .addAllMemoryProfile(allocData.threads
                                    .entrySet()
                                    .stream()
                                    .map(entry -> ProfilerFileProto.MemoryProfileV2.newBuilder()
                                            .setThread(entry.getKey())
                                            .setBytes(entry.getValue().calculateTimeTaken())
                                            .addAllChildren(entry.getValue().toMemoryProfile(dictionary).getChildrenList())
                                            .build())
                                    .collect(Collectors.toList()))

                            .setDictionary(dictionary.toProto())
                    ));
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

    private record FinalProfileData(
            Map<String, ProfileSection> threads, int samples) {
    }
}
