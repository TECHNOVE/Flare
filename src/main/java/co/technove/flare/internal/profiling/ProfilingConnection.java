package co.technove.flare.internal.profiling;

import co.technove.flare.FlareAuth;
import co.technove.flare.exceptions.UserReportableException;
import co.technove.flare.proto.ProfilerFileProto;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.ParseException;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.zip.GZIPOutputStream;

class ProfilingConnection {

    private final FlareAuth flareAuth;
    private final HttpClient client;

    private final String id;
    private final String key;

    public ProfilingConnection(FlareAuth flareAuth, ProfilerFileProto.CreateProfile profilerCreator) throws UserReportableException {
        this.flareAuth = flareAuth;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        JsonObject object = this.request(profilerCreator::writeTo, "create");
        this.id = object.getString("id", null);
        this.key = object.getString("key", null);
        if (this.id == null || this.key == null) {
            throw new UserReportableException("Received invalid response from Flare server, please check logs", new IOException("Invalid response from Flare server: " + object));
        }
    }

    public void sendNewData(ProfilerFileProto.AirplaneProfileFile file) throws UserReportableException {
        this.request(file::writeTo, this.id, this.key);
    }

    public void sendTimelineData(ProfilerFileProto.TimelineFile file) throws UserReportableException {
        this.request(file::writeTo, this.id, this.key, "timeline");
    }

    private JsonObject request(Writer writer, String... path) throws UserReportableException {
        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try (OutputStream stream = new GZIPOutputStream(data)) {
                writer.writeTo(stream);
            }

            URI uri = this.flareAuth.getUri().resolve("/" + String.join("/", path));

            HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", "token " + this.flareAuth.getToken())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data.toByteArray()))
                    .build(), HttpResponse.BodyHandlers.ofString());

            String body = response.body();

            JsonObject object;
            try {
                object = Json.parse(body).asObject();
            } catch (ParseException e) {
                throw new UserReportableException("Received invalid data from Flare server", new IOException("URI: " + uri + " Body:\n" + body.substring(0, Math.min(100, body.length()))));
            }

            if (response.statusCode() != 200) {
                throw new IOException("Error occurred sending data: Failed to open connection to profile server, code: " + response.statusCode() + " msg: " + object);
            }

            if (object.getBoolean("error", false)) {
                throw new UserReportableException("Error from Flare server: " + object.getString("message", "unknown error"));
            }

            return object;
        } catch (IOException | InterruptedException e) {
            throw new UserReportableException("Failed connecting to Flare server", e);
        }
    }

    public @NotNull String getId() {
        return id;
    }

    private interface Writer {
        void writeTo(OutputStream outputStream) throws IOException;
    }
}
