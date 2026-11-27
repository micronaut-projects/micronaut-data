package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.PathDirection;
import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.graph.tck.entities.Friend;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRepository extends CrudRepository<Friend, String> {

    @PathLimit(10)
    Optional<GraphPath<Friend>> findShortestPath(Friend from, Friend to);

    Optional<GraphPath<Friend>> findShortestPathByFriends(String from, String to, @PathLimit int maxDepth);

    @PathLimit(4)
    List<GraphPath<Friend>> findAllPathsByFriends(String from, String to);

    @PathLimit(10)
    boolean existsPath(String from, String to);

    List<GraphRelationship<Friend, Friend>> findRelationships(String from, String to);

    long countRelationshipsByFriends();

    @PathLimit(4)
    boolean existsPathByFriends(String from, String to, @PathDirection Direction direction);

    long countRelationshipsByFriends(@PathDirection Direction direction);
}
