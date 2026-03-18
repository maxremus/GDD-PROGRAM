package org.example.gp.entity;

public enum FilingStatus {
    SUBMITTED("Подаване"),
    PENDING("Изчакване"),
    NOT_REQUIRED("Не се подава");

    private final String label;

    FilingStatus(String label) {
        this.label = label;
    }


    public String getLabel() {
        return label;
    }
}
