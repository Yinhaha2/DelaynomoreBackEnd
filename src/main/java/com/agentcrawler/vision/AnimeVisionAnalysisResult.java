package com.agentcrawler.vision;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AnimeVisionAnalysisResult {

    @JsonProperty("work_title")
    private String workTitle = "";

    private List<String> characters = new ArrayList<>();

    @JsonProperty("episode_hint")
    private String episodeHint = "";

    @JsonProperty("search_scene")
    private String searchScene = "";

    @JsonProperty("visual_features")
    private List<String> visualFeatures = new ArrayList<>();

    private double confidence;

    public String getWorkTitle() {
        return workTitle;
    }

    public void setWorkTitle(String workTitle) {
        this.workTitle = workTitle == null ? "" : workTitle.trim();
    }

    public List<String> getCharacters() {
        return characters;
    }

    public void setCharacters(List<String> characters) {
        this.characters = characters == null ? new ArrayList<>() : new ArrayList<>(characters);
    }

    public String getEpisodeHint() {
        return episodeHint;
    }

    public void setEpisodeHint(String episodeHint) {
        this.episodeHint = episodeHint == null ? "" : episodeHint.trim();
    }

    public String getSearchScene() {
        return searchScene;
    }

    public void setSearchScene(String searchScene) {
        this.searchScene = searchScene == null ? "" : searchScene.trim();
    }

    public List<String> getVisualFeatures() {
        return visualFeatures;
    }

    public void setVisualFeatures(List<String> visualFeatures) {
        this.visualFeatures = visualFeatures == null ? new ArrayList<>() : new ArrayList<>(visualFeatures);
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String visualFeaturesSummary() {
        return String.join("、", visualFeatures);
    }

    public boolean hasWorkTitle() {
        return workTitle != null && !workTitle.isBlank();
    }
}
