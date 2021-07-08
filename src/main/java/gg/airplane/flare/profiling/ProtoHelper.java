package gg.airplane.flare.profiling;

import com.google.common.collect.Iterators;
import gg.airplane.flare.Flare;
import gg.airplane.flare.live.Collector;
import gg.airplane.flare.live.CollectorData;
import gg.airplane.flare.live.EventCollector;
import gg.airplane.flare.live.LiveCollector;
import gg.airplane.flare.live.category.GraphCategory;
import gg.airplane.flare.live.formatter.DataFormatter;
import gg.airplane.flare.proto.ProfilerFileProto;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProtoHelper {

    // move here because I hated seeing it in ProfileController
    public static ProfilerFileProto.CreateProfile createProfile(Flare flare, List<EventCollector> eventCollectors, List<LiveCollector> liveCollectors) {
        List<ProfilerFileProto.CreateProfile.ConfigurationFile> files = new ArrayList<>();

        flare.getServerData().getFiles().forEach((key, value) -> {
            files.add(ProfilerFileProto.CreateProfile.ConfigurationFile.newBuilder()
              .setFilename(key)
              .setContents(value)
              .build());
        });

        Map<String, ProfilerFileProto.CreateProfile.TimelineData> timelineData = new HashMap<>();
        Map<GraphCategory, Set<String>> categoryMap = new HashMap<>();
        for (Iterator<Collector> it = Iterators.concat(eventCollectors.iterator(), liveCollectors.iterator()); it.hasNext(); ) {
            Collector collector = it.next();
            for (CollectorData data : collector.getDataTypes()) {
                timelineData.put(data.getId(), ProfilerFileProto.CreateProfile.TimelineData.newBuilder()
                  .setName(data.getName())
                  .setDescription(data.getDescription())
                  .setFormat(data.getFormatter().map(DataFormatter::getId).orElse(null))
                  .build());

                data.getGraphCategory().ifPresent(cat -> {
                    categoryMap
                      .computeIfAbsent(cat, name -> new HashSet<>())
                      .add(data.getId());
                });
            }
        }

        return ProfilerFileProto.CreateProfile.newBuilder()
          .setFormat(ProfilerFileProto.CreateProfile.Format.THREE_ZERO)
          .addAllConfigs(files)
          .setHwinfo(flare.getHardwareInfo())
          .setVmoptions(ProfilerFileProto.CreateProfile.VMOptions.newBuilder()
            .setVersion(System.getProperty("java.version"))
            .setVendor(System.getProperty("java.vendor"))
            .setVm(System.getProperty("java.vm.name"))
            .setRuntimeName(System.getProperty("java.runtime.name"))
            .setRuntimeVersion(System.getProperty("java.runtime.version"))
            .addAllFlags(ManagementFactory.getRuntimeMXBean().getInputArguments())
            .build())
          .setOs(flare.getOperatingSystem())
          .setV3(ProfilerFileProto.CreateProfile.V3.newBuilder()
            .putAllVersions(flare.getServerData().getVersions())
            .putAllTimelineData(timelineData)
            .addAllGraphCategories(categoryMap.entrySet().stream()
              .map(entry -> ProfilerFileProto.CreateProfile.GraphCategory.newBuilder()
                .setName(entry.getKey().getName())
                .addAllTypes(entry.getValue())
                .setDefault(flare.getDefaultCategories().contains(entry.getKey()))
                .build()).collect(Collectors.toList()))
            .build())
          .build();
    }

    public static ProfilerFileProto.TimelineFile createTimeline(List<EventCollector> eventCollectors, List<LiveCollector> liveCollectors, long startedAt, long stoppedAt) {
        return ProfilerFileProto.TimelineFile.newBuilder()
          .setStartedAt(startedAt)
          .setStoppedAt(stoppedAt)
          .addAllEvents(eventCollectors.stream().map(eventCollector -> {
              return eventCollector.getAndCopyEvents().stream().map(event -> ProfilerFileProto.TimelineFile.EventData.newBuilder()
                .setType(event.getData().getId())
                .setTime(event.getTime())
                .setDuration(Math.toIntExact(event.getDuration()))
                .putAllMetadata(event.getMetadata())
                .build()).collect(Collectors.toList());
          })
            .flatMap(List::stream)
            .collect(Collectors.toList()))
          .addAllLive(liveCollectors.stream().map(liveCollector -> {
              return liveCollector.useDataThenClear(map -> map.entrySet().stream().map(entry -> ProfilerFileProto.TimelineFile.LiveData.newBuilder()
                .setType(entry.getKey().getId())
                .addAllData(entry.getValue())
                .build()).collect(Collectors.toList()));
          }).flatMap(List::stream).collect(Collectors.toList()))
          .build();
    }
}
