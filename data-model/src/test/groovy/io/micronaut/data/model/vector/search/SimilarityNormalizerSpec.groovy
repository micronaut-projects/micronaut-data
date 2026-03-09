package io.micronaut.data.model.vector.search

import spock.lang.Specification

class SimilarityNormalizerSpec extends Specification {

    def "cosine conversion is reversible"() {
        given:
        def normalizer = SimilarityNormalizer.forScoringFunction(ScoringFunction.COSINE)

        expect:
        normalizer.getSimilarity(0d) == 1d
        normalizer.getSimilarity(1d) == 0.5d
        normalizer.getScore(0.5d) == 1d
    }

    def "euclidean conversion is reversible"() {
        given:
        def normalizer = SimilarityNormalizer.forScoringFunction(ScoringFunction.EUCLIDEAN)

        expect:
        normalizer.getSimilarity(0d) == 1d
        normalizer.getScore(1d) == 0d
        normalizer.getScore(0.5d) == 1d
    }

    def "dot product conversion is reversible"() {
        given:
        def normalizer = SimilarityNormalizer.forScoringFunction(ScoringFunction.DOT_PRODUCT)

        expect:
        normalizer.getSimilarity(1d) == 0d
        normalizer.getSimilarity(-1d) == 1d
        normalizer.getScore(0.5d) == 0d
    }

    def "inner product uses dot product normalization"() {
        expect:
        SimilarityNormalizer.forScoringFunction(ScoringFunction.INNER_PRODUCT).getScore(0.25d) ==
            SimilarityNormalizer.forScoringFunction(ScoringFunction.DOT_PRODUCT).getScore(0.25d)
    }

    def "taxicab uses identity normalization"() {
        given:
        def normalizer = SimilarityNormalizer.forScoringFunction(ScoringFunction.TAXICAB)

        expect:
        normalizer.getSimilarity(0.42d) == 0.42d
        normalizer.getScore(0.42d) == 0.42d
    }
}
