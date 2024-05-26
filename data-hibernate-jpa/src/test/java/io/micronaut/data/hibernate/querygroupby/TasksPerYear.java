package io.micronaut.data.hibernate.querygroupby;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class TasksPerYear {
    private final String number;
    private final String year;

    public TasksPerYear(String number, String year) {
        this.number = number;
        this.year = year;
    }

    public String getNumber() {
        return number;
    }

    public String getYear() {
        return year;
    }
}
