package co.technove.flare;

import org.jetbrains.annotations.NotNull;

import java.net.URI;

public class FlareAuth {
    private final String token;
    private final URI uri;

    private FlareAuth(@NotNull String token, @NotNull URI uri) {
        this.token = token;
        this.uri = uri;
    }

    public static FlareAuth fromTokenAndUrl(@NotNull String token, @NotNull URI uri) {
        return new FlareAuth(token, uri);
    }

    public @NotNull String getToken() {
        return token;
    }

    public @NotNull URI getUri() {
        return uri;
    }
}
