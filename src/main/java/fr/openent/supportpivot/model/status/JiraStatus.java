package fr.openent.supportpivot.model.status;

import io.vertx.core.json.JsonArray;

import java.util.List;
import java.util.stream.Collectors;

public class JiraStatus extends StatusModel {
    private final List<String> nameList;

    public JiraStatus(String key, JsonArray nameList) {
        super(key);
        this.nameList = nameList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toList());
    }

    public boolean contains(String name) {
        return nameList.contains(name);
    }

    public List<String> getNameList() {
        return nameList;
    }
}
