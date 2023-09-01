package io.micronaut.data.hibernate.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The person class with various relation between persona (child, parent, friends).
 */
@Entity
public class RelPerson {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToMany
    @JoinTable(name = "person_friends")
    private Set<RelPerson> friends = new LinkedHashSet<>();

    @OneToMany(mappedBy = "parent")
    private Set<RelPerson> children = new LinkedHashSet<>();

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private RelPerson parent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<RelPerson> getFriends() {
        return friends;
    }

    public void setFriends(Set<RelPerson> friends) {
        this.friends = friends;
    }

    public Set<RelPerson> getChildren() {
        return children;
    }

    public void setChildren(Set<RelPerson> children) {
        this.children = children;
    }

    public RelPerson getParent() {
        return parent;
    }

    public void setParent(RelPerson parent) {
        this.parent = parent;
    }
}
