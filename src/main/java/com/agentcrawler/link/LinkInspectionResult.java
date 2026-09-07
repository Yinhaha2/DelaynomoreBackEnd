package com.agentcrawler.link;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LinkInspectionResult {

    private String url;
    @JsonProperty("final_url")
    private String finalUrl;
    private LinkKind kind;
    private boolean alive = true;
    @JsonProperty("http_status")
    private Integer httpStatus;
    @JsonProperty("content_type")
    private String contentType;
    private String title;
    private String description;
    private String image;
    @JsonProperty("work_title")
    private String workTitle;
    @JsonProperty("episode_hint")
    private String episodeHint;
    @JsonProperty("site_hint")
    private String siteHint;
    @JsonProperty("media_id")
    private String mediaId;
    @JsonProperty("info_hash")
    private String infoHash;
    @JsonProperty("display_name")
    private String displayName;
    private double confidence;
    private String error;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFinalUrl() {
        return finalUrl;
    }

    public void setFinalUrl(String finalUrl) {
        this.finalUrl = finalUrl;
    }

    public LinkKind getKind() {
        return kind;
    }

    public void setKind(LinkKind kind) {
        this.kind = kind;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getWorkTitle() {
        return workTitle;
    }

    public void setWorkTitle(String workTitle) {
        this.workTitle = workTitle;
    }

    public String getEpisodeHint() {
        return episodeHint;
    }

    public void setEpisodeHint(String episodeHint) {
        this.episodeHint = episodeHint;
    }

    public String getSiteHint() {
        return siteHint;
    }

    public void setSiteHint(String siteHint) {
        this.siteHint = siteHint;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    public String getInfoHash() {
        return infoHash;
    }

    public void setInfoHash(String infoHash) {
        this.infoHash = infoHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean hasWorkTitle() {
        return workTitle != null && !workTitle.isBlank();
    }
}
