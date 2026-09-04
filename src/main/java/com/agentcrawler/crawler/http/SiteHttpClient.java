package com.agentcrawler.crawler.http;

import com.agentcrawler.crawler.model.PreparedRuleRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SiteHttpClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        return execute(builder.build());
    }

    public String postForm(String url, Map<String, String> form, Map<String, String> headers) throws IOException {
        FormBody.Builder formBuilder = new FormBody.Builder();
        form.forEach(formBuilder::add);
        Request.Builder builder = new Request.Builder().url(url).post(formBuilder.build());
        headers.forEach(builder::header);
        return execute(builder.build());
    }

    public String execute(PreparedRuleRequest request, Map<String, String> headers) throws IOException {
        Map<String, String> mergedHeaders = new LinkedHashMap<>(headers);
        request.headers().forEach(mergedHeaders::putIfAbsent);
        HttpUrl httpUrl = buildUrl(request.url(), request.query());
        if ("POST".equalsIgnoreCase(request.method())) {
            RequestBody body = buildBody(request, mergedHeaders);
            Request.Builder builder = new Request.Builder().url(httpUrl).post(body);
            mergedHeaders.forEach(builder::header);
            return execute(builder.build());
        }
        Request.Builder builder = new Request.Builder().url(httpUrl).get();
        mergedHeaders.forEach(builder::header);
        return execute(builder.build());
    }

    private RequestBody buildBody(PreparedRuleRequest request, Map<String, String> headers) throws IOException {
        if ("json".equalsIgnoreCase(request.bodyType())) {
            headers.putIfAbsent("Content-Type", "application/json");
            String json = request.body() == null ? "{}" : MAPPER.writeValueAsString(request.body());
            return RequestBody.create(json, JSON);
        }
        if ("form".equalsIgnoreCase(request.bodyType())) {
            headers.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
            FormBody.Builder formBuilder = new FormBody.Builder();
            if (request.body() instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    formBuilder.add(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return formBuilder.build();
        }
        return RequestBody.create("", JSON);
    }

    private HttpUrl buildUrl(String url, Map<String, String> query) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
        HttpUrl.Builder builder = parsed.newBuilder();
        query.forEach(builder::addQueryParameter);
        return builder.build();
    }

    private String execute(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " for " + request.url());
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
