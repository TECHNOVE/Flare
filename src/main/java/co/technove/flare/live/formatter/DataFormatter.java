package co.technove.flare.live.formatter;

public class DataFormatter {

    public static final DataFormatter PERCENT = new DataFormatter("builtin:percent");
    public static final DataFormatter BYTES = new DataFormatter("builtin:bytes");
    public static final DataFormatter MILLISECONDS = new DataFormatter("builtin:ms");

    private final String id;

    public DataFormatter(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
