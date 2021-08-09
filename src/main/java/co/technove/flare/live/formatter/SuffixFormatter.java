package co.technove.flare.live.formatter;

public class SuffixFormatter extends DataFormatter {

    public SuffixFormatter(String singular, String plural) {
        super("builtin:suffix:" + singular + ":" + plural);

        if (singular.contains(":") || plural.contains(":")) {
            throw new IllegalArgumentException("no : allowed");
        }
    }

    public static SuffixFormatter of(String suffix) {
        return new SuffixFormatter(suffix, suffix);
    }
}
