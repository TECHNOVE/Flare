package gg.airplane.flare.live.builtin;

import com.sun.management.OperatingSystemMXBean;
import gg.airplane.flare.live.CollectorData;
import gg.airplane.flare.live.LiveCollector;
import gg.airplane.flare.live.category.GraphCategory;
import gg.airplane.flare.live.formatter.DataFormatter;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.lang.management.ManagementFactory;

public class StatCollector extends LiveCollector {

    private static final CollectorData CPU = new CollectorData("builtin:stat:cpu", "CPU Load", "The total amount of CPU usage across all cores.", DataFormatter.PERCENT, GraphCategory.SYSTEM);
    private static final CollectorData CPU_PROCESS = new CollectorData("builtin:stat:cpu_process", "Process CPU", "The amount of CPU being used by this process.", DataFormatter.PERCENT, GraphCategory.SYSTEM);
    private static final CollectorData MEMORY = new CollectorData("builtin:stat:memory_used", "Memory", "The amount of memory being used currently.", DataFormatter.BYTES, GraphCategory.SYSTEM);
    private static final CollectorData MEMORY_TOTAL = new CollectorData("builtin:stat:memory_total", "Memory Total", "The total amount of memory allocated.", DataFormatter.BYTES, GraphCategory.SYSTEM);

    private final OperatingSystemMXBean bean;
    private final CentralProcessor processor;

    public StatCollector() {
        super(CPU, CPU_PROCESS, MEMORY, MEMORY_TOTAL);

        this.bean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.processor = new SystemInfo().getHardware().getProcessor();
    }

    @Override
    public void run() {
        Runtime runtime = Runtime.getRuntime();

        this.report(CPU, this.processor.getSystemLoadAverage(1)[0] / 100); // percentage
        this.report(CPU_PROCESS, this.bean.getProcessCpuLoad());
        this.report(MEMORY, runtime.totalMemory() - runtime.freeMemory());
        this.report(MEMORY_TOTAL, runtime.totalMemory());
    }
}
