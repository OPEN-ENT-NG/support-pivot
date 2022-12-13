package fr.openent.supportpivot.model.status.config;

import io.vertx.core.json.JsonArray;

import java.util.List;
import java.util.stream.Collectors;

public class JiraStatusConfig extends StatusConfigModel {
    private final List<String> nameList;

    public JiraStatusConfig(String key, JsonArray nameList) {
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
