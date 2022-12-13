package fr.openent.supportpivot.enums;

import java.util.Arrays;

public enum PriorityEnum {
    HIGHEST("Highest", "Bloquante", "Bloquant"),
    HIGH("High", "Majeure", "Majeur"),
    LOWEST("Lowest", "Mineure", "Mineur");

    private final String enName;
    private final String frName;
    private final String pivotName;

    PriorityEnum(String enName, String frName, String pivotName) {
        this.enName = enName;
        this.frName = frName;
        this.pivotName = pivotName;
    }

    public static PriorityEnum getValue(String string) {
        return Arrays.stream(PriorityEnum.values())
                .filter(priorityEnum -> priorityEnum.enName.equals(string) || priorityEnum.frName.equals(string) || priorityEnum.pivotName.equals(string))
                .findFirst()
                .orElse(PriorityEnum.LOWEST);
    }

    public String getEnName() {
        return enName;
    }

    public String getFrName() {
        return frName;
    }

    public String getPivotName() {
        return pivotName;
    }
}
