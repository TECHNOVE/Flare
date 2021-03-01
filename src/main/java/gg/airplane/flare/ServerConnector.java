package gg.airplane.flare;

import java.util.Map;
import java.util.logging.Level;

public abstract class ServerConnector {
    public static ServerConnector connector;

    public abstract String getPluginForClass(String name);

    public abstract Thread getMainThread();

    public abstract Map<String, String> getConfigurations();

    public abstract void log(Level level, String message);

    public abstract void log(Level level, String message, Throwable t);

    public abstract String getPrimaryVersion();

    public abstract String getApiVersion();

    public abstract String getMcVersion();

    public abstract void schedule(Runnable runnable, long tick1, long tick2);

    public abstract void cancel(Runnable runnable);

    public abstract void runAsync(Runnable runnable);

    public abstract String getWebUrl();

    public abstract String getToken();
}
