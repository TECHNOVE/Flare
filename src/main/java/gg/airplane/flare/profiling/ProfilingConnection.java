package gg.airplane.flare.profiling;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import gg.airplane.flare.FlareAuth;
import gg.airplane.flare.exceptions.UserReportableException;
import gg.airplane.flare.proto.ProfilerFileProto;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

class ProfilingConnection {

    private final FlareAuth flareAuth;
    private final String id;
    private final String key;

    public ProfilingConnection(FlareAuth flareAuth, ProfilerFileProto.CreateProfile profilerCreator) throws UserReportableException {
        this.flareAuth = flareAuth;

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(this.flareAuth.getUrl() + "/create");

            post.setHeader("Authorization", "token " + this.flareAuth.getToken());

            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try (OutputStream stream = new GZIPOutputStream(data)) {
                profilerCreator.writeTo(stream);
            }
            post.setEntity(new ByteArrayEntity(data.toByteArray(), ContentType.DEFAULT_BINARY));

            try (CloseableHttpResponse response = client.execute(post)) {
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                response.getEntity().writeTo(byteStream);

                JsonObject object = Json.parse(byteStream.toString()).asObject();
                if (object.getBoolean("error", false)) {
                    throw new UserReportableException("Error occurred starting Flare: " + object.getString("message", "unknown error"));
                }

                this.id = object.getString("id", null);
                this.key = object.getString("key", null);
                if (this.id == null || this.key == null) {
                    throw new UserReportableException("Received invalid response from Flare server, please check logs", new IOException("Invalid response from Flare server: " + object));
                }
            } catch (IOException e) {
                throw new UserReportableException("Failed to connect to " + post.getRequestUri(), e);
            }
        } catch (IOException e) {
            throw new UserReportableException("Failed initial connection to Flare server", e);
        }
    }

    public void sendNewData(ProfilerFileProto.AirplaneProfileFile file) throws UserReportableException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(this.flareAuth.getUrl() + "/" + this.id + "/" + this.key);

            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try (OutputStream stream = new GZIPOutputStream(data)) {
                file.writeTo(stream);
            }
            post.setEntity(new ByteArrayEntity(data.toByteArray(), ContentType.DEFAULT_BINARY));

            try (CloseableHttpResponse response = client.execute(post)) {
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                response.getEntity().writeTo(byteStream);

                JsonObject object = Json.parse(byteStream.toString()).asObject();
                if (object.getBoolean("error", false)) {
                    throw new UserReportableException("Error occurred sending Flare: " + object.getString("message", "unknown error"));
                }

                if (response.getCode() != 200) {
                    throw new IOException("Error occurred sending data: Failed to open connection to profile server, code: " + response.getCode() + " msg: " + object);
                }
            } catch (IOException e) {
                throw new UserReportableException("Failed to connect to " + post.getRequestUri(), e);
            }
        } catch (IOException e) {
            throw new UserReportableException("Failed to connect to Flare server", e);
        }
    }

    public void sendTimelineData(ProfilerFileProto.TimelineFile file) throws UserReportableException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(this.flareAuth.getUrl() + "/" + this.id + "/" + this.key + "/timeline");

            ByteArrayOutputStream data = new ByteArrayOutputStream();
            try (OutputStream stream = new GZIPOutputStream(data)) {
                file.writeTo(stream);
            }
            post.setEntity(new ByteArrayEntity(data.toByteArray(), ContentType.DEFAULT_BINARY));

            try (CloseableHttpResponse response = client.execute(post)) {
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                response.getEntity().writeTo(byteStream);

                JsonObject object = Json.parse(byteStream.toString()).asObject();
                if (object.getBoolean("error", false)) {
                    throw new UserReportableException("Error occurred sending Flare: " + object.getString("message", "unknown error"));
                }

                if (response.getCode() != 200) {
                    throw new IOException("Error occurred sending data: Failed to open connection to profile server, code: " + response.getCode() + " msg: " + object);
                }
            } catch (IOException e) {
                throw new UserReportableException("Failed to connect to " + post.getRequestUri(), e);
            }
        } catch (IOException e) {
            throw new UserReportableException("Failed to connect to Flare server", e);
        }
    }

    public String getId() {
        return id;
    }
}
