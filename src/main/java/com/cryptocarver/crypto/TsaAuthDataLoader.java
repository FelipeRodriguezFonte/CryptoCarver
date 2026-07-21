package com.cryptocarver.crypto;

import eu.europa.esig.dss.spi.client.http.DataLoader;
import com.cryptocarver.model.TsaAuthCredentials;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;

public class TsaAuthDataLoader implements DataLoader {
    private final TsaAuthCredentials auth;
    private String contentType;
    private int connectTimeoutMs = 15_000;
    private int readTimeoutMs = 20_000;
    private int maxResponseBytes = 1024 * 1024; // 1MB default

    public TsaAuthDataLoader(TsaAuthCredentials auth) {
        this.auth = auth;
    }

    public TsaAuthDataLoader(TsaAuthCredentials auth, int connectTimeoutMs, int readTimeoutMs, int maxResponseBytes) {
        this.auth = auth;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public byte[] get(String url) {
        throw new UnsupportedOperationException("GET is not supported for TSA requests");
    }

    @Override
    public DataAndUrl get(List<String> urls) {
        throw new UnsupportedOperationException("GET is not supported for TSA requests");
    }

    @Override
    public byte[] post(String url, byte[] content) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            if (auth != null) {
                String header = auth.getAuthorizationHeader();
                if (header != null) connection.setRequestProperty("Authorization", header);
            }
            connection.setDoOutput(true);
            if (contentType != null) {
                connection.setRequestProperty("Content-Type", contentType);
            }
            connection.setRequestProperty("Accept", "application/timestamp-reply");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);

            try (var output = connection.getOutputStream()) {
                output.write(content);
            }

            int status = connection.getResponseCode();
            try (InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream()) {

                byte[] payload = new byte[0];
                if (stream != null) {
                    payload = stream.readNBytes(maxResponseBytes);
                    if (stream.read() != -1) {
                        throw new IllegalArgumentException("TSA response exceeded maximum size limit of " + maxResponseBytes + " bytes");
                    }
                }

                if (status < 200 || status >= 300) {
                    throw new RuntimeException("TSA returned HTTP " + status);
                }
                return payload;
            }
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e.getMessage());
        } catch (java.net.SocketTimeoutException e) {
            throw new RuntimeException("TSA request timed out");
        } catch (java.io.IOException e) {
            throw new RuntimeException("Transport error during TSA request");
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error during TSA request");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
