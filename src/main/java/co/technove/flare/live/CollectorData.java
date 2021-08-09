package co.technove.flare.live;

import co.technove.flare.live.category.GraphCategory;
import co.technove.flare.live.formatter.DataFormatter;

import javax.annotation.Nullable;
import java.util.Optional;

public class CollectorData {
    private final String id;
    private final String name;
    private final String description;
    @Nullable
    private final DataFormatter formatter;
    @Nullable
    private final GraphCategory graphCategory;

    public CollectorData(String id, String name, String description) {
        this(id, name, description, null, null);
    }

    public CollectorData(String id, String name, String description, @Nullable GraphCategory graphCategory) {
        this(id, name, description, null, graphCategory);
    }

    public CollectorData(String id, String name, String description, @Nullable DataFormatter formatter) {
        this(id, name, description, formatter, null);
    }

    public CollectorData(String id, String name, String description, @Nullable DataFormatter formatter, @Nullable GraphCategory graphCategory) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.formatter = formatter;
        this.graphCategory = graphCategory;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Optional<DataFormatter> getFormatter() {
        return Optional.ofNullable(formatter);
    }

    public Optional<GraphCategory> getGraphCategory() {
        return Optional.ofNullable(this.graphCategory);
    }
}
