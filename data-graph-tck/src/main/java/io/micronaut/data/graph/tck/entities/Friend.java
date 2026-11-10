package io.micronaut.data.graph.tck.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node
public class Friend {
    @Id
    private final String name;
    @Relationship("FRIENDS")
    private List<Friend> friends = new ArrayList<>();

    public Friend(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<Friend> getFriends() {
        return friends;
    }

    public void setFriends(List<Friend> friends) {
        this.friends = friends;
    }
}
