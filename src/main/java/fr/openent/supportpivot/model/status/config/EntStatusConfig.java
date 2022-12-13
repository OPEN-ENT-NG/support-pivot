package fr.openent.supportpivot.model.status.config;

public class EntStatusConfig extends StatusConfigModel {
    private final String name;
    public EntStatusConfig(String key, String name) {
        super(key);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
