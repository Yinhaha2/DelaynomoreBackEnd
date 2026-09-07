package com.agentcrawler.agent.session;

import java.util.ArrayList;
import java.util.List;

public class AnimeSessionState {
    private String workTitle = "";
    private List<String> characters = new ArrayList<>();
    private String currentEpisode = "";
    private String searchScene = "";
    private String visualFeatures = "";
    private double confidence;
    private boolean locked;

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

    public void addCharacter(String character) {
        if (character == null || character.isBlank()) {
            return;
        }
        String trimmed = character.trim();
        if (!characters.contains(trimmed)) {
            characters.add(trimmed);
        }
    }

    public String getCurrentEpisode() {
        return currentEpisode;
    }

    public void setCurrentEpisode(String currentEpisode) {
        this.currentEpisode = currentEpisode == null ? "" : currentEpisode.trim();
    }

    public String getSearchScene() {
        return searchScene;
    }

    public void setSearchScene(String searchScene) {
        this.searchScene = searchScene == null ? "" : searchScene.trim();
    }

    public String getVisualFeatures() {
        return visualFeatures;
    }

    public void setVisualFeatures(String visualFeatures) {
        this.visualFeatures = visualFeatures == null ? "" : visualFeatures.trim();
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean hasAnchor() {
        return locked && !workTitle.isBlank();
    }
}
