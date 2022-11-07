package io.micronaut.data.hibernate.querygroupby;

public enum TaskStatus {
    TO_DO("To Do"),
    IN_PROGRESS("In Progress"),
    ON_HOLD("On Hold"),
    DONE("Done");
    private final String label;

    private TaskStatus(String label) {
        this.label = label;
    }
    public String getLabel() {
        return label;
    }
}
