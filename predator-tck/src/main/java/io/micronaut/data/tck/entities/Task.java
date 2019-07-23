package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class Task {
    @Id
    @GeneratedValue
    private Long taskId;

    private final String name;

    public Task(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }
}
