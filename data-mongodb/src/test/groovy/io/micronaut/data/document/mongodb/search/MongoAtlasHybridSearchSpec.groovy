package io.micronaut.data.document.mongodb.search

import groovy.transform.Memoized
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.search.tck.AbstractHybridSearchSpec

/**
 * Runs the hybrid search TCK against MongoDB Search and {@code $vectorSearch} on the Atlas local deployment.
 */
class MongoAtlasHybridSearchSpec extends AbstractHybridSearchSpec implements MongoAtlasLocal {

    @Memoized
    @Override
    def getArticleRepository() {
        return context.getBean(MongoAtlasArticleHybridSearchRepository)
    }

    @Override
    def newArticle(String title, String body, Vector embedding) {
        return new AtlasArticle(title, body, embedding as FloatVector)
    }

    @Override
    void awaitIndexing() {
        awaitSearchIndex("atlas_article")
        awaitVectorIndex("atlas_article", "embedding", 3)
    }

    void "a derived vector finder ranks by cosine distance and honours the maximum distance"() {
        when:
        def results = articleRepository.searchByEmbeddingNear(COFFEE_LIKE, new Score(0.5))

        then:
        results.results()*.entity()*.title == ["Cooking with Coffee", "Coffee Roasting"]
        results.results()[0].score().value() < 1e-6
        results.results()[1].score().value() > 0 && results.results()[1].score().value() < 0.1
        articleRepository.findTop2ByEmbeddingNear(Vector.of([0f, 1f, 0f] as float[]), 2d)*.title == ["Baking Bread", "Java Concurrency"]
    }
}
