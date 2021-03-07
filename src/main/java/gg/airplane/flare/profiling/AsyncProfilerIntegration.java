package gg.airplane.flare.profiling;

import com.sun.jna.Platform;
import gg.airplane.flare.ProfileType;
import gg.airplane.flare.ServerConnector;
import gg.airplane.flare.proto.ProfilerFileProto;
import one.jfr.ClassRef;
import one.jfr.Dictionary;
import one.jfr.JfrReader;
import one.jfr.MethodRef;
import one.profiler.AsyncProfiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AsyncProfilerIntegration {

    private static ProfileType currentProfile;
    private static AsyncProfiler profiler;
    private static long startTime = 0;
    private static String disabledReason = "";
    private static Path tempdir = null;
    private static String profileFile = null;
    private static int interval;

    public static void init() {
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

    private static String execute(String command) throws IOException {
        String val = profiler.execute(command);
        System.out.println("[Airplane] " + command + " -> " + val);
        return val;
    }

    synchronized static void startProfiling(ProfileType type, int interval) throws IOException {
        if (currentProfile != null) {
            throw new RuntimeException("Profiling already started.");
        }
        currentProfile = type;
        AsyncProfilerIntegration.interval = interval;
        Thread mainThread = ServerConnector.connector.getMainThread();

        tempdir = Files.createTempDirectory("flare");
        profileFile = tempdir.resolve("flare.jfr").toString();

        String returned;
        if (type == ProfileType.ALLOC) {
            returned = execute("start,event=" + type.getInternalName() + ",interval=" + interval + "ms,jstackdepth=1024,jfr,file=" + profileFile);
        } else {
            returned = execute("start,event=" + type.getInternalName() + ",interval=" + interval + "ms,threads,filter,jstackdepth=1024,jfr,file=" + profileFile);
            profiler.addThread(mainThread);
        }
        if (!returned.trim().equals("Profiling started")) {
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

    private static final int FRAME_KERNEL = 5;

    protected enum JFRMethodType {
        JAVA('J'),
        KERNEL('K'),
        NATIVE('N');

        private final char prefix;

        JFRMethodType(char prefix) {
            this.prefix = prefix;
        }

        public char getPrefix() {
            return prefix;
        }
    }

    protected static class JFRMethod {
        private final JFRMethodType type;

        private final String classString;
        private final String methodStr;
        private final String signatureStr;

        private final String fullPath;

        public JFRMethod(JFRMethodType type, String classString, String methodStr, String signatureStr, String fullPath) {
            this.type = type;
            this.classString = classString;
            this.methodStr = methodStr;
            this.signatureStr = signatureStr;
            this.fullPath = fullPath;
        }

        public JFRMethodType getType() {
            return type;
        }

        public String getClassString() {
            return classString;
        }

        public String getMethodStr() {
            return methodStr;
        }

        public String getSignatureStr() {
            return signatureStr;
        }

        public String getFullPath() {
            return fullPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JFRMethod jfrMethod = (JFRMethod) o;
            return type == jfrMethod.type && Objects.equals(classString, jfrMethod.classString) && Objects.equals(methodStr, jfrMethod.methodStr) && Objects.equals(signatureStr, jfrMethod.signatureStr) && Objects.equals(fullPath, jfrMethod.fullPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, classString, methodStr, signatureStr, fullPath);
        }

        @Override
        public String toString() {
            return this.type.prefix + this.fullPath;
        }
    }

    private static JFRMethod getMethodName(long methodId, int type, JfrReader reader, Dictionary<JFRMethod> methodNames) {
        JFRMethod result = methodNames.get(methodId);
        if (result != null) {
            return result;
        }

        MethodRef method = reader.methods.get(methodId);
        ClassRef cls = reader.classes.get(method.cls);
        byte[] className = reader.symbols.get(cls.name);
        byte[] methodName = reader.symbols.get(method.name);
        byte[] signature = reader.symbols.get(method.sig);

        if (className == null || className.length == 0) {
            String methodStr = new String(methodName, StandardCharsets.UTF_8);
            result = new JFRMethod(type == FRAME_KERNEL ? JFRMethodType.KERNEL : JFRMethodType.NATIVE, null, null, null, methodStr);
        } else {
            String classStr = new String(className, StandardCharsets.UTF_8).replace("/", ".");
            String methodStr = new String(methodName, StandardCharsets.UTF_8);
            String signatureStr = new String(signature, StandardCharsets.UTF_8);
            result = new JFRMethod(JFRMethodType.JAVA, classStr, methodStr, signatureStr, classStr + '.' + methodStr + signatureStr);
        }

        methodNames.put(methodId, result);
        return result;
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

        Dictionary<JFRMethod> methodNames = new Dictionary<>(); // method names cache
        try (JfrReader reader = new JfrReader(profileFile)) {
            Map<String, ProfileSection> threadsMap = new HashMap<>();
            reader.stackTraces.forEach((stackId, stackTrace) -> {
                List<String> threads = stackTrace.samples.stream().map(sampleId -> reader.threads.get(reader.samples.get(sampleId).tid)).collect(Collectors.toList());

                long[] methods = stackTrace.methods;
                byte[] types = stackTrace.types;

                for (String thread : threads) {
//                    System.out.println("---- " + stackTrace.samples + " " + thread);
                    ProfileSection lastSection = null;
                    for (int i = methods.length - 1; i >= 0; i--) {
                        JFRMethod method = getMethodName(methods[i], types[i], reader, methodNames);
//                        System.out.println(method);
                        if (lastSection == null) {
                            lastSection = threadsMap.computeIfAbsent(thread, t -> new ProfileSection(method, currentProfile));
                        } else {
                            lastSection = lastSection.getSection(method);
                        }
                    }

                    if (lastSection != null) {
                        lastSection.setSamples(stackTrace.samples.size());
                        lastSection.setTimeTakenNs((long) interval * stackTrace.samples.size());
                    }
//                    System.out.println();
                }
            });

            for (ProfileSection value : threadsMap.values()) {
//                System.out.println(value.print(0));
            }

            ProfilerFileProto.AirplaneProfileFile.Builder builder = ProfilerFileProto.AirplaneProfileFile.newBuilder()
              .setInfo(ProfilerFileProto.AirplaneProfileFile.ProfileInfo.newBuilder()
                .setSamples(reader.samples.getSize())
                .setTimeMs(reader.durationNanos / 1000000)
                .build());

            if (currentProfile == ProfileType.ALLOC) {
                return builder
                  .setData(ProfilerFileProto.AirplaneProfileFile.ProfileData.newBuilder()
                    .setMemoryProfile(ProfilerFileProto.MemoryProfile.newBuilder()
                      .addAllChildren(threadsMap
                        .entrySet()
                        .stream()
                        .map(entry -> ProfilerFileProto.MemoryProfile.Children.newBuilder()
                          .setName(entry.getKey())
                          .setBytes((int) entry.getValue().calculateTimeTaken())
                          .addChildren(entry.getValue().toMemoryProfile())
                          .build())
                        .collect(Collectors.toList())
                      )
                    )
                  );
            } else {
                return builder
                  .setData(ProfilerFileProto.AirplaneProfileFile.ProfileData.newBuilder()
                    .setTimeProfile(ProfilerFileProto.TimeProfile.newBuilder()
                      .addAllChildren(threadsMap
                        .entrySet()
                        .stream()
                        .map(entry -> ProfilerFileProto.TimeProfile.Children.newBuilder()
                          .setName(entry.getKey())
                          .setTime(entry.getValue().calculateTimeTaken())
                          .setSamples(1)
                          .addChildren(entry.getValue().toTimeChild())
                          .build())
                        .collect(Collectors.toList())
                      )
                    )
                  );
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            currentProfile = null;
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
