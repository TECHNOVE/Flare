package co.technove.flare.live;

import co.technove.flare.Flare;
import co.technove.flare.internal.util.DoubleArrayList;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public abstract class LiveCollector extends Collector implements Runnable {
    private final Map<CollectorData, DoubleArrayList> data = new HashMap<>();
    protected Duration interval = Duration.ofSeconds(5);

    public LiveCollector(CollectorData... data) {
        for (CollectorData datum : data) {
            this.data.put(datum, new DoubleArrayList());
        }
    }

    public Duration getInterval() {
        return interval;
    }

    @Override
    public Collection<CollectorData> getDataTypes() {
        return this.data.keySet();
    }

    @Override
    public void start(Flare flare) {
        flare.getIntervalManager().schedule(this, interval);
    }

    protected void report(CollectorData collectorData, double data) {
        synchronized (this.data) {
            Objects.requireNonNull(this.data.get(collectorData)).addDouble(data);
        }
    }

    public <T> T useDataThenClear(Function<Map<CollectorData, DoubleArrayList>, T> function) {
        synchronized (this.data) {
            T returned = function.apply(this.data);
            this.data.values().forEach(List::clear);
            return returned;
        }
    }
}
