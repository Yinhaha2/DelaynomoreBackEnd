package com.agentcrawler.crawler.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiChapterConfig {
    private ApiRequestConfig request = new ApiRequestConfig();
    private String format = "nested";
    private String roadsPath = "$.data.roads[*]";
    private String roadNamePath = "$.name";
    private String episodesPath = "$.episodes[*]";
    private String episodeNamePath = "$.name";
    private String episodeUrlPath = "$.url";
    private String roadNamesPath = "";
    private String roadEpisodesPath = "";
    private String roadSeparator = "$$$";
    private String episodeSeparator = "#";
    private String fieldSeparator = "$";
    private Map<String, String> variables = new LinkedHashMap<>();
    private ApiEpisodePageConfig episodePage;

    public ApiRequestConfig getRequest() {
        return request;
    }

    public void setRequest(ApiRequestConfig request) {
        this.request = request == null ? new ApiRequestConfig() : request;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getRoadsPath() {
        return roadsPath;
    }

    public void setRoadsPath(String roadsPath) {
        this.roadsPath = roadsPath;
    }

    public String getRoadNamePath() {
        return roadNamePath;
    }

    public void setRoadNamePath(String roadNamePath) {
        this.roadNamePath = roadNamePath;
    }

    public String getEpisodesPath() {
        return episodesPath;
    }

    public void setEpisodesPath(String episodesPath) {
        this.episodesPath = episodesPath;
    }

    public String getEpisodeNamePath() {
        return episodeNamePath;
    }

    public void setEpisodeNamePath(String episodeNamePath) {
        this.episodeNamePath = episodeNamePath;
    }

    public String getEpisodeUrlPath() {
        return episodeUrlPath;
    }

    public void setEpisodeUrlPath(String episodeUrlPath) {
        this.episodeUrlPath = episodeUrlPath;
    }

    public String getRoadNamesPath() {
        return roadNamesPath;
    }

    public void setRoadNamesPath(String roadNamesPath) {
        this.roadNamesPath = roadNamesPath;
    }

    public String getRoadEpisodesPath() {
        return roadEpisodesPath;
    }

    public void setRoadEpisodesPath(String roadEpisodesPath) {
        this.roadEpisodesPath = roadEpisodesPath;
    }

    public String getRoadSeparator() {
        return roadSeparator;
    }

    public void setRoadSeparator(String roadSeparator) {
        this.roadSeparator = roadSeparator;
    }

    public String getEpisodeSeparator() {
        return episodeSeparator;
    }

    public void setEpisodeSeparator(String episodeSeparator) {
        this.episodeSeparator = episodeSeparator;
    }

    public String getFieldSeparator() {
        return fieldSeparator;
    }

    public void setFieldSeparator(String fieldSeparator) {
        this.fieldSeparator = fieldSeparator;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables == null ? new LinkedHashMap<>() : variables;
    }

    public ApiEpisodePageConfig getEpisodePage() {
        return episodePage;
    }

    public void setEpisodePage(ApiEpisodePageConfig episodePage) {
        this.episodePage = episodePage;
    }

    public boolean isDelimited() {
        return "delimited".equalsIgnoreCase(format);
    }
}
