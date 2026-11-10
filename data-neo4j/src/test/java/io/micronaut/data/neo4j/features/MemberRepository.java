package io.micronaut.data.neo4j.features;

import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@Neo4jRepository
public interface MemberRepository extends CrudRepository<Member, String> {

    @PathLimit(3)
    boolean existsPathByMentorBetweenNameAndId(String from, String to);

    Optional<GraphPath<Object>> findShortestPath(Member from, Officer to);
}
