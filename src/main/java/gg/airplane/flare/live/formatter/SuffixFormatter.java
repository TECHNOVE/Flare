package gg.airplane.flare.live.formatter;

public class SuffixFormatter extends DataFormatter {

    public static SuffixFormatter of(String suffix) {
        return new SuffixFormatter(suffix, suffix);
    }

    public SuffixFormatter(String singular, String plural) {
        super("builtin:suffix:" + singular + ":" + plural);

        if (singular.contains(":") || plural.contains(":")) {
            throw new IllegalArgumentException("no : allowed");
        }
    }
}
