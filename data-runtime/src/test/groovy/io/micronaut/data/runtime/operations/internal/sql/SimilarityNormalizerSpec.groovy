package io.micronaut.data.runtime.operations.internal.sql

import io.micronaut.data.model.vector.search.ScoringFunction
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
        def normalizer = SimilarityNormalizer.forScoringFunction(ScoringFunction.L2_EUCLIDEAN)

        expect:
        normalizer.getSimilarity(0d) == 1d
        normalizer.getScore(1d) == 0d
        normalizer.getScore(0.5d) == 1d
    }

    def "dot conversion is reversible"() {
        given:
        def normalizer = SimilarityNormalizer.forScoringFunction(ScoringFunction.DOT)

        expect:
        normalizer.getSimilarity(1d) == 0d
        normalizer.getSimilarity(-1d) == 1d
        normalizer.getScore(0.5d) == 0d
    }

    def "l1 manhattan uses identity normalization"() {
        given:
        def normalizer = SimilarityNormalizer.forScoringFunction(ScoringFunction.L1_MANHATTAN)

        expect:
        normalizer.getSimilarity(0.42d) == 0.42d
        normalizer.getScore(0.42d) == 0.42d
    }

    def "l2 euclidean squared conversion is reversible"() {
        given:
        def normalizer = SimilarityNormalizer.forScoringFunction(ScoringFunction.L2_EUCLIDEAN_SQUARED)

        expect:
        normalizer.getSimilarity(0d) == 1d
        normalizer.getScore(1d) == 0d
        normalizer.getScore(0.5d) == 1d
    }
}
