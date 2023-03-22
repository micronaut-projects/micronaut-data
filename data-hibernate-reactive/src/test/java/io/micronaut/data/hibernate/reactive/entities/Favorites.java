package io.micronaut.data.hibernate.reactive.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class Favorites {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private Integer version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "sort_index")
    private List<FavoriteStudents> list = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public List<FavoriteStudents> getList() {
        return list;
    }

    public void setList(List<FavoriteStudents> list) {
        this.list = list;
    }
}
