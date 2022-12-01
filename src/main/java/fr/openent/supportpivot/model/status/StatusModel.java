package fr.openent.supportpivot.model.status;

public abstract class StatusModel {
    private final String key;

    protected StatusModel(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
