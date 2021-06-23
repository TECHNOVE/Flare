package gg.airplane.flare;

public class FlareAuth {
    private final String token;
    private final String url;

    public FlareAuth(String token, String url) {
        this.token = token;
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public String getUrl() {
        return url;
    }
}
