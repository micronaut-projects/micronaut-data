package io.micronaut.data.document.mongodb.search;

import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.SearchRepository;

import java.util.List;
import java.util.UUID;

/**
 * The TCK {@link io.micronaut.data.search.tck.repositories.BookPrefixSearchRepository} methods over {@link AtlasBook}.
 */
@MongoRepository
public interface MongoAtlasBookSearchRepository extends CrudRepository<AtlasBook, UUID>, SearchRepository<AtlasBook, UUID> {

    List<AtlasBook> findByTitleSearch(String query);

    List<AtlasBook> findByTitleSearchNative(String query);

    SearchResults<AtlasBook> searchByTitleSearchAll(String query);

    List<AtlasBook> findBySearchAndAuthor(String query, String author);

    List<AtlasBook> findBySearchOrderByPages(String query);

    long countBySearchAll(String query);

    List<AtlasBook> findByTitleSearchAndPagesGreaterThan(String query, int pages);

    SearchResults<AtlasBook> searchBySearch(String query, Pageable pageable);

    CursoredPage<AtlasBook> findAll(CursoredPageable pageable);

    SearchResults<AtlasBook> searchByDescriptionSearchPhrase(String query);

    List<AtlasBook> findByTitleSearchPrefix(String query);

    List<AtlasBook> findByTitleSearchFuzzy(String query);

    SearchResults<AtlasBook> searchBySearch(String query, Score minScore);
}
