package gg.airplane.flare.profiling;

import com.sun.jna.Platform;
import gg.airplane.flare.ProfileType;
import gg.airplane.flare.ServerConnector;
import gg.airplane.flare.collectors.GCCollector;
import gg.airplane.flare.collectors.ThreadState;
import gg.airplane.flare.proto.ProfilerFileProto;
import one.profiler.AsyncProfiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AsyncProfilerIntegration {

    private static boolean initialized = false;

    private static ProfileType currentProfile;
    private static AsyncProfiler profiler;
    private static long startTime = 0;
    private static String disabledReason = "";
    private static final Set<String> activeThreads = new HashSet<>();

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
                ServerConnector.connector.log(Level.WARNING, "Skipping profiling support, missing flags -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints");
                disabledReason = "Flags -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints were not added.";
                return;
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
        return currentProfile != null;
    }

    private static void execute(String command) {
        try {
            System.out.println("[Airplane] " + command + " -> " + profiler.execute(command));
//            profiler.execute(command);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    synchronized static void startProfiling(ProfileType type, int interval) throws IOException {
        if (currentProfile != null) {
            throw new RuntimeException("Profiling already started.");
        }
        currentProfile = type;
        Thread mainThread = ServerConnector.connector.getMainThread();

        String returned;
        if (type == ProfileType.ALLOC) {
            returned = profiler.execute("start,event=" + type.getInternalName() + ",interval=" + interval + "ms,jstackdepth=1024");
        } else {
            returned = profiler.execute("start,event=" + type.getInternalName() + ",interval=" + interval + "ms,threads,filter,jstackdepth=1024");
            profiler.addThread(mainThread);
            for (Thread activeThread : ThreadState.getActiveThreads()) {
                profiler.addThread(activeThread);
                activeThreads.add(activeThread.getName());
            }
        }
        if ((!returned.contains("Started ") || !returned.contains(" profiling")) && !returned.contains("Profiling started")) {
            throw new IOException("Failed to start flare: " + returned.trim());
        }
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

    private static boolean isInt(String strNum) {
        if (strNum == null) {
            return false;
        }
        try {
            double d = Integer.parseInt(strNum);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    synchronized static ProfilerFileProto.AirplaneProfileFile.Builder stopProfiling() {
        if (currentProfile == null) {
            throw new RuntimeException("Flare has not been started.");
        }
        try {
            profiler.stop();
        } catch (Throwable t) {
            currentProfile = null;
            throw t;
        }

        try {
            String results = profiler.execute("traces=1024,sig");
            String[] lines = results.split("\n");

            final ProfileSection head = new ProfileSection("Head", currentProfile);

            List<String> currentWorkingSection = new ArrayList<>();

            AtomicInteger totalSamples = new AtomicInteger();

            Runnable process = () -> {
                if (currentWorkingSection.isEmpty()) {
                    return;
                }
                String fullHeader = currentWorkingSection.get(0);
                if (!fullHeader.startsWith("---")) {
                    currentWorkingSection.clear();
                    return;
                }
                String header = fullHeader.replaceAll("-", "").trim();
                if (header.equals("Execution profile")) {
                    if (currentWorkingSection.size() > 1) {
                        if (currentWorkingSection.get(1).trim().startsWith("Total Samples")) {
                            totalSamples.set(Integer.parseInt(currentWorkingSection.get(1).split(":")[1].trim()));
                        }
                    }
                    currentWorkingSection.clear();
                    return; // ignore for now, has some useful stats though
                }
                String[] splitHeader = header.split(" ");
                long ns = Long.parseUnsignedLong(splitHeader[0]);
                int samples = Integer.parseInt(header.split(",")[1].trim().split(" ")[0]);
                ProfileSection section = head;

                for (int i = currentWorkingSection.size() - 1; i >= 1; i--) {
                    String[] split1 = currentWorkingSection.get(i).split("] ");
                    if (split1.length < 2) {
                        continue;
                    }
                    String line = split1[1].trim();
                    if (line.startsWith("[") && (line.contains(" tid") || line.startsWith("[tid="))) {
                        String[] split = line.split(" tid=");
                        line = split[0].substring(1);
                        if (section == head && (currentProfile == ProfileType.ALLOC || !activeThreads.contains(line))) {
                            currentWorkingSection.clear();
                            return;
                        }
                    }
                    section = section.getSection(line);
                }

                section.setTimeTakenNs(ns);
                section.setSamples(samples);
                currentWorkingSection.clear();
            };

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.startsWith("---")) {
                    process.run(); // process previous section
                }
                String trimmed = line.trim();
                if (trimmed.length() > 0) {
                    currentWorkingSection.add(trimmed);
                }
            }

            process.run();

            Map<String, ProfileSection> toMerge = new HashMap<>(0);
            for (Iterator<Map.Entry<String, ProfileSection>> iterator = head.getSections().entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<String, ProfileSection> entry = iterator.next();
                String s = entry.getKey();
                String[] split = s.split("-");
                if (split.length > 0 && isInt(split[split.length - 1])) {
                    String newKey = s.substring(0, s.lastIndexOf("-"));
                    toMerge.put(newKey, entry.getValue());
                    iterator.remove();
                }
            }
            for (Map.Entry<String, ProfileSection> entry : toMerge.entrySet()) {
                head.getSections().computeIfAbsent(entry.getKey(), k -> new ProfileSection(k, currentProfile)).merge(entry.getValue());
            }

            Collection<ProfileSection> threads = head.getSections().values();

            long time = System.currentTimeMillis() - startTime;
            ProfilerFileProto.AirplaneProfileFile.Builder builder = ProfilerFileProto.AirplaneProfileFile.newBuilder()
              .setInfo(ProfilerFileProto.AirplaneProfileFile.ProfileInfo.newBuilder()
                .setSamples(totalSamples.get())
                .setTimeMs(time)
                .build());
            if (currentProfile == ProfileType.ALLOC) {
                return builder
                  .setData(ProfilerFileProto.AirplaneProfileFile.ProfileData.newBuilder()
                    .setMemoryProfile(ProfilerFileProto.MemoryProfile.newBuilder()
                      .addAllChildren(threads.stream().map(ProfileSection::toMemoryProfile).collect(Collectors.toList()))
                      .build())
                    .build());
            } else {
                return builder
                  .setData(ProfilerFileProto.AirplaneProfileFile.ProfileData.newBuilder()
                    .setTimeProfile(ProfilerFileProto.TimeProfile.newBuilder()
                      .addAllChildren(threads.stream().map(ProfileSection::toTimeChild).collect(Collectors.toList()))
                      .build())
                    .build());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            currentProfile = null;
            activeThreads.clear();
        }
    }
}
