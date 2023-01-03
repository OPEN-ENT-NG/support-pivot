package fr.openent.supportpivot.enums;

public enum SourceEnum {
    ENT("ENT"),
    JIRA("JIRA"),
    IWS("IWS"),
    LDE("LDE");

    private final String name;
    public String toString(){return name;}

    SourceEnum(String name) { this.name = name;   }
}
