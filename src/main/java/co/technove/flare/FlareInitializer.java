package co.technove.flare;

import co.technove.flare.internal.FlareInternal;
import co.technove.flare.internal.profiling.InitializationException;

import java.util.List;

public class FlareInitializer {
    public static List<String> initialize() throws InitializationException {
        return FlareInternal.initialize();
    }
}
