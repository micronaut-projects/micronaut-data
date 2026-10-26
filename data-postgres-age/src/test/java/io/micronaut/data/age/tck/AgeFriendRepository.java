package io.micronaut.data.age.tck;

import io.micronaut.data.age.annotation.AgeRepository;
import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.graph.tck.entities.Friend;
import io.micronaut.data.graph.tck.repositories.FriendRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

@AgeRepository
public interface AgeFriendRepository extends FriendRepository {

    @PathLimit(5)
    Page<GraphPath<Friend>> findPathByFriends(String from, String to, Pageable pageable);

    @PathLimit(5)
    Page<GraphPath<Friend>> findAllShortestPathsByFriends(String from, String to, Pageable pageable);
}
