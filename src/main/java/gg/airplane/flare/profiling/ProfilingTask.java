package gg.airplane.flare.profiling;

import gg.airplane.flare.ProfileType;
import gg.airplane.flare.ServerConnector;
import gg.airplane.flare.profiling.dictionary.ProfileDictionary;
import gg.airplane.flare.proto.ProfilerFileProto;

import java.io.IOException;

class ProfilingTask implements Runnable {

    private final ProfileType type;
    private final int interval;
    //    private final List<ProfilerFileProto.AirplaneProfileFile.TimeData> slices = new ArrayList<>();
    private long startedAt;

    public ProfilingTask(ProfileType type, int interval) throws IOException {
        this.type = type;
        this.interval = interval;

        this.start();
    }

    public void start() throws IOException {
        this.startedAt = System.currentTimeMillis();
        AsyncProfilerIntegration.startProfiling(this.type, this.interval);
        ServerConnector.connector.schedule(this, 3 * 20, 3 * 20);
    }

    @Override
    public void run() {
//        this.slices.add(TimeSlice.collect(3, TimeUnit.SECONDS).toTimeData());
    }

    public ProfilerFileProto.AirplaneProfileFile stop(ProfileDictionary dictionary) {
        ServerConnector.connector.cancel(this);

        return AsyncProfilerIntegration.stopProfiling(dictionary)
//          .addAllTimeData(this.slices)
          .setStartedAt(this.startedAt)
          .setStoppedAt(System.currentTimeMillis())
          .build();
    }

}
