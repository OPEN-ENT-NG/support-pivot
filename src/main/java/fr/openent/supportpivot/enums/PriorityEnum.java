package fr.openent.supportpivot.enums;

import java.util.Arrays;

public enum PriorityEnum {
    HIGHEST("Highest", "Bloquante"),
    HIGH("High", "Majeure"),
    LOWEST("Lowest", "Mineure");

    private final String enName;
    private final String frName;

    PriorityEnum(String enName, String frName) {
        this.enName = enName;
        this.frName = frName;
    }

    public static PriorityEnum getValue(String string) {
        return Arrays.stream(PriorityEnum.values())
                .filter(priorityEnum -> priorityEnum.enName.equals(string) || priorityEnum.frName.equals(string))
                .findFirst()
                .orElse(PriorityEnum.LOWEST);
    }

    public String getEnName() {
        return enName;
    }

    public String getFrName() {
        return frName;
    }
}
