package com.agentcrawler.crawler.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginRule {
    private String name;
    private String baseURL;
    private String searchURL;
    private String searchList;
    private String searchName;
    private String searchResult;
    private String searchImage;
    private String chapterRoads;
    private String chapterResult;
    private String userAgent = "";
    private String referer = "";
    private boolean usePost = false;
    private String searchMode = "xpath";
    private String chapterMode = "xpath";
    private Boolean enabled;
    private ApiSearchConfig searchApiConfig = new ApiSearchConfig();
    private ApiChapterConfig chapterApiConfig = new ApiChapterConfig();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("baseURL")
    public String getBaseURL() {
        return baseURL;
    }

    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    @JsonProperty("searchURL")
    public String getSearchURL() {
        return searchURL;
    }

    public void setSearchURL(String searchURL) {
        this.searchURL = searchURL;
    }

    public String getSearchList() {
        return searchList;
    }

    public void setSearchList(String searchList) {
        this.searchList = searchList;
    }

    public String getSearchName() {
        return searchName;
    }

    public void setSearchName(String searchName) {
        this.searchName = searchName;
    }

    public String getSearchResult() {
        return searchResult;
    }

    public void setSearchResult(String searchResult) {
        this.searchResult = searchResult;
    }

    public String getSearchImage() {
        return searchImage;
    }

    public void setSearchImage(String searchImage) {
        this.searchImage = searchImage;
    }

    public String getChapterRoads() {
        return chapterRoads;
    }

    public void setChapterRoads(String chapterRoads) {
        this.chapterRoads = chapterRoads;
    }

    public String getChapterResult() {
        return chapterResult;
    }

    public void setChapterResult(String chapterResult) {
        this.chapterResult = chapterResult;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public boolean isUsePost() {
        return usePost;
    }

    public void setUsePost(boolean usePost) {
        this.usePost = usePost;
    }

    public String getSearchMode() {
        return searchMode;
    }

    public void setSearchMode(String searchMode) {
        this.searchMode = searchMode;
    }

    public String getChapterMode() {
        return chapterMode;
    }

    public void setChapterMode(String chapterMode) {
        this.chapterMode = chapterMode;
    }

    public ApiSearchConfig getSearchApiConfig() {
        return searchApiConfig;
    }

    public void setSearchApiConfig(ApiSearchConfig searchApiConfig) {
        this.searchApiConfig = searchApiConfig == null ? new ApiSearchConfig() : searchApiConfig;
    }

    public ApiChapterConfig getChapterApiConfig() {
        return chapterApiConfig;
    }

    public void setChapterApiConfig(ApiChapterConfig chapterApiConfig) {
        this.chapterApiConfig = chapterApiConfig == null ? new ApiChapterConfig() : chapterApiConfig;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public boolean isPlaceholder() {
        String host = baseURL == null ? "" : baseURL.toLowerCase();
        return host.contains("example.") || host.contains(".test/") || host.endsWith(".test");
    }

    public boolean isSearchApiMode() {
        return RuleMode.isApi(searchMode);
    }

    public boolean isChapterApiMode() {
        return RuleMode.isApi(chapterMode);
    }
}
