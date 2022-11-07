package io.micronaut.data.hibernate.entities;

import io.micronaut.data.hibernate.querygroupby.TaskStatus;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
public class MicronautTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false, updatable = false)
    private String uuid = UUID.randomUUID().toString();
    private String name;
    private String description;
    private LocalDate dueDate;

    private TaskStatus status;

    @ManyToOne(optional = false)
    private MicronautProject project;


    public MicronautTask() {
    }
    public MicronautTask(String name, String description, LocalDate dueDate, MicronautProject project) {
        this(name, description, dueDate, project, TaskStatus.TO_DO);
    }

    public MicronautTask(String name, String description, LocalDate dueDate, MicronautProject project, TaskStatus status) {
        this.name = name;
        this.description = description;
        this.dueDate = dueDate;
        this.status = status;
        this.project = project;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MicronautTask other = (MicronautTask) obj;
        if (uuid == null) {
            if (other.uuid != null)
                return false;
        } else if (!uuid.equals(other.uuid))
            return false;
        return true;
    }
    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public MicronautProject getProject() {
        return project;
    }

    public void setProject(MicronautProject project) {
        this.project = project;
    }


    @Override
    public String toString() {
        return "Task [id=" + id + ", name=" + name + ", description=" + description + ", dueDate=" + dueDate + ", status=" + status + ", project=" + project + "]";
    }

}
