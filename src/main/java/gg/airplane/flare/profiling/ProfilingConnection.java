package gg.airplane.flare.profiling;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import gg.airplane.flare.FlareAuth;
import gg.airplane.flare.exceptions.UserReportableException;
import gg.airplane.flare.proto.ProfilerFileProto;

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

        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try (OutputStream stream = new GZIPOutputStream(data)) {
                profilerCreator.writeTo(stream);
            }

            HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                    .uri(URI.create(this.flareAuth.getUrl() + "/create"))
                    .header("Authorization", "token " + this.flareAuth.getToken())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data.toByteArray()))
                    .build(), HttpResponse.BodyHandlers.ofString());

            String body = response.body();

            JsonObject object = Json.parse(body).asObject();
            if (object.getBoolean("error", false)) {
                throw new UserReportableException("Error occurred starting Flare: " + object.getString("message", "unknown error"));
            }

            this.id = object.getString("id", null);
            this.key = object.getString("key", null);
            if (this.id == null || this.key == null) {
                throw new UserReportableException("Received invalid response from Flare server, please check logs", new IOException("Invalid response from Flare server: " + object));
            }
        } catch (IOException | InterruptedException e) {
            throw new UserReportableException("Failed initial connection to Flare server", e);
        }
    }

    public void sendNewData(ProfilerFileProto.AirplaneProfileFile file) throws UserReportableException {
        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try (OutputStream stream = new GZIPOutputStream(data)) {
                file.writeTo(stream);
            }

            HttpResponse<String> response = this.client.send(HttpRequest.newBuilder()
                    .uri(URI.create(this.flareAuth.getUrl() + "/" + this.id + "/" + this.key))
                    .header("Authorization", "token " + this.flareAuth.getToken())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data.toByteArray()))
                    .build(), HttpResponse.BodyHandlers.ofString());

            JsonObject object = Json.parse(response.body()).asObject();
            if (object.getBoolean("error", false)) {
                throw new UserReportableException("Error occurred sending Flare: " + object.getString("message", "unknown error"));
            }

            if (response.statusCode() != 200) {
                throw new IOException("Error occurred sending data: Failed to open connection to profile server, code: " + response.statusCode() + " msg: " + object);
            }
        } catch (IOException | InterruptedException e) {
            throw new UserReportableException("Failed to connect to Flare server", e);
        }
    }

    public void sendTimelineData(ProfilerFileProto.TimelineFile file) throws UserReportableException {
        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try (OutputStream stream = new GZIPOutputStream(data)) {
                file.writeTo(stream);
            }

            HttpResponse<String> response = this.client.send(HttpRequest.newBuilder()
                    .uri(URI.create(this.flareAuth.getUrl() + "/" + this.id + "/" + this.key + "/timeline"))
                    .header("Authorization", "token " + this.flareAuth.getToken())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(data.toByteArray()))
                    .build(), HttpResponse.BodyHandlers.ofString());

            JsonObject object = Json.parse(response.body()).asObject();
            if (object.getBoolean("error", false)) {
                throw new UserReportableException("Error occurred sending Flare: " + object.getString("message", "unknown error"));
            }

            if (response.statusCode() != 200) {
                throw new IOException("Error occurred sending data: Failed to open connection to profile server, code: " + response.statusCode() + " msg: " + object);
            }
        } catch (IOException | InterruptedException e) {
            throw new UserReportableException("Failed to connect to Flare server", e);
        }
    }

    public String getId() {
        return id;
    }
}
