package com.agentcrawler.crawler.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiSearchConfig {
    private ApiRequestConfig request = new ApiRequestConfig();
    private String listPath = "$.data[*]";
    private String namePath = "$.name";
    private String sourcePath = "$.url";
    private String imagePath = "";

    public ApiRequestConfig getRequest() {
        return request;
    }

    public void setRequest(ApiRequestConfig request) {
        this.request = request == null ? new ApiRequestConfig() : request;
    }

    public String getListPath() {
        return listPath;
    }

    public void setListPath(String listPath) {
        this.listPath = listPath;
    }

    public String getNamePath() {
        return namePath;
    }

    public void setNamePath(String namePath) {
        this.namePath = namePath;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}
