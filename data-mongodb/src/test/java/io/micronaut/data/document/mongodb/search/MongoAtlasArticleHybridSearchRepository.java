package io.micronaut.data.document.mongodb.search;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.SearchRepository;

import java.util.List;
import java.util.UUID;

/**
 * The TCK {@link io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository} methods over
 * {@link AtlasArticle}, plus derived vector finders.
 */
@MongoRepository
public interface MongoAtlasArticleHybridSearchRepository extends CrudRepository<AtlasArticle, UUID>, SearchRepository<AtlasArticle, UUID> {

    SearchResults<AtlasArticle> searchByTitleSearch(String query);

    SearchResults<AtlasArticle> searchByTitleSearchAndEmbeddingNear(String query, Vector vector);

    SearchResults<AtlasArticle> searchBySearchAndEmbeddingNear(String query, Vector vector, Pageable pageable);

    SearchResults<AtlasArticle> searchByEmbeddingNear(Vector vector, Score maxDistance);

    List<AtlasArticle> findTop2ByEmbeddingNear(Vector vector, Double maxDistance);
}
