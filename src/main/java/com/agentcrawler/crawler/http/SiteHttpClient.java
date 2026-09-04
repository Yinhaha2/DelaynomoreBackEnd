package com.agentcrawler.crawler.http;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SiteHttpClient {
    private final OkHttpClient client;

    public SiteHttpClient(int timeoutSeconds) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    public String getText(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).get();
        headers.forEach(builder::header);
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            return response.body().string();
        }
    }

    public String postForm(String url, Map<String, String> form, Map<String, String> headers) throws IOException {
        FormBody.Builder formBuilder = new FormBody.Builder();
        form.forEach(formBuilder::add);
        RequestBody body = formBuilder.build();
        Request.Builder builder = new Request.Builder().url(url).post(body);
        headers.forEach(builder::header);
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            return response.body().string();
        }
    }

    public static Map<String, String> parseQuery(String url) {
        URI uri = URI.create(url);
        Map<String, String> params = new LinkedHashMap<>();
        if (uri.getRawQuery() == null) {
            return params;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                params.put(parts[0], parts[1]);
            }
        }
        return params;
    }

    public static String stripQuery(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
    }
}
