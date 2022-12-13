package fr.openent.supportpivot.model.status.config;

public abstract class StatusConfigModel {
    private final String key;

    protected StatusConfigModel(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
