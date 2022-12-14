package io.micronaut.data.hibernate.entities;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
public class Favorites {

    @Id
    @Column(columnDefinition = "uuid")
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
