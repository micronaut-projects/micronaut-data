package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@Neo4jRepository
public interface TagRepository extends CrudRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    List<Tag> findByNameStartsWithOrderByNameDesc(String prefix, Sort sort);

    List<Tag> findFirst2ByNameStartsWith(String prefix, Sort sort);

    List<TagSummary> findAllByNameStartsWith(String prefix);

    List<Tag> findByNameIlike(String pattern);

    CursoredPage<Tag> findByNameStartsWith(String prefix, Pageable pageable);

    @Query("MATCH (n:Tag) RETURN n, ' LIMIT ' AS text")
    List<Tag> withLimitInLiteral(Sort sort);

    @Query("MATCH (n:Tag) RETURN n limit 2")
    List<Tag> lowerCaseLimit(Sort sort);

    @Query("MATCH (n:Tag) RETURN n limit 2")
    List<Tag> lowerCaseLimitPaged(Pageable pageable);

    @Query("MATCH (n:Tag) RETURN n // the tags")
    List<Tag> withTrailingComment(Sort sort);

    @Query("MATCH (n:Tag) RETURN n")
    List<Object[]> tagRows();

    @Query("MATCH (n:Tag) RETURN n.name AS name, 1 AS one")
    List<Object[]> tagColumns();


    @Query("MATCH (t:Tag) RETURN t")
    List<Tag> allTags(Sort sort);

    @Query("MATCH (n:Tag) RETURN n LIMIT 1")
    List<Tag> firstTag(Pageable pageable);

    Page<Tag> findAll(Pageable pageable);

    @Query("MATCH (t:Tag) WITH t ORDER BY t.name LIMIT 2 RETURN t")
    List<Tag> findFirstTwo(Pageable pageable);

    @Query("MATCH (t:Tag) RETURN t ORDER BY t.name LIMIT 2")
    List<Tag> findTwo(Pageable pageable);
}
