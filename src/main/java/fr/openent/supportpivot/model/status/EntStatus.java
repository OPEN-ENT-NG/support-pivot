package fr.openent.supportpivot.model.status;

public class EntStatus extends StatusModel {
    private final String name;
    public EntStatus(String key, String name) {
        super(key);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
