package fr.openent.supportpivot.model.status;

import io.vertx.core.json.JsonArray;

import java.util.List;

public class JiraStatus extends StatusModel {
    private final List<String> nameList;

    public JiraStatus(String key, JsonArray nameList) {
        super(key);
        this.nameList = nameList.getList();
    }

    public boolean contains(String name) {
        return nameList.contains(name);
    }

    public List<String> getNameList() {
        return nameList;
    }
}
