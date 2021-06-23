package gg.airplane.flare.live.category;

public class GraphCategory {
    public static final GraphCategory SYSTEM = new GraphCategory("System");

    private final String name;

    public GraphCategory(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
