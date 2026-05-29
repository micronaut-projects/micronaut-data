/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.vector.search.ScoringFunction;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * Converts dialect-specific vector scores into normalized similarities and back.
 *
 * @since 5.0.0
 */
@Internal
public final class SimilarityNormalizer {

    private static final SimilarityNormalizer IDENTITY =
        new SimilarityNormalizer(DoubleUnaryOperator.identity(), DoubleUnaryOperator.identity());

    private static final SimilarityNormalizer L2_EUCLIDEAN =
        new SimilarityNormalizer(
            scoreValue -> 1d / (1d + Math.pow(scoreValue, 2d)),
            similarityValue -> similarityValue == 0d ? Double.MAX_VALUE : Math.sqrt((1d / similarityValue) - 1d)
        );

    private static final SimilarityNormalizer L2_EUCLIDEAN_SQUARED =
        new SimilarityNormalizer(
            scoreValue -> 1d / (1d + scoreValue),
            similarityValue -> similarityValue == 0d ? Double.MAX_VALUE : (1d / similarityValue) - 1d
        );

    private static final SimilarityNormalizer COSINE =
        new SimilarityNormalizer(
            scoreValue -> (1d + (1d - scoreValue)) / 2d,
            similarityValue -> 1d - ((similarityValue * 2d) - 1d)
        );

    private static final SimilarityNormalizer DOT_PRODUCT =
        new SimilarityNormalizer(
            scoreValue -> (1d - scoreValue) / 2d,
            similarityValue -> 1d - (similarityValue * 2d)
        );

    private static final Map<ScoringFunction, SimilarityNormalizer> NORMALIZERS = new EnumMap<>(ScoringFunction.class);

    static {
        NORMALIZERS.put(ScoringFunction.L2_EUCLIDEAN, L2_EUCLIDEAN);
        NORMALIZERS.put(ScoringFunction.L2_EUCLIDEAN_SQUARED, L2_EUCLIDEAN_SQUARED);
        NORMALIZERS.put(ScoringFunction.COSINE, COSINE);
        NORMALIZERS.put(ScoringFunction.DOT, DOT_PRODUCT);
        NORMALIZERS.put(ScoringFunction.L1_MANHATTAN, IDENTITY);
    }

    private final DoubleUnaryOperator similarity;
    private final DoubleUnaryOperator score;

    private SimilarityNormalizer(DoubleUnaryOperator similarity, DoubleUnaryOperator score) {
        this.similarity = similarity;
        this.score = score;
    }

    /**
     * Returns identity score/similarity normalization.
     *
     * @return identity normalizer
     */
    public static SimilarityNormalizer identity() {
        return IDENTITY;
    }

    /**
     * Resolves normalizer for a scoring function.
     *
     * @param scoringFunction scoring function
     * @return matching normalizer or identity when no explicit mapping exists
     */
    public static SimilarityNormalizer forScoringFunction(ScoringFunction scoringFunction) {
        SimilarityNormalizer normalizer = NORMALIZERS.get(scoringFunction);
        return normalizer == null ? IDENTITY : normalizer;
    }

    /**
     * Converts score into normalized similarity.
     *
     * @param scoreValue raw score value
     * @return normalized similarity value
     */
    public double getSimilarity(double scoreValue) {
        return similarity.applyAsDouble(scoreValue);
    }

    /**
     * Converts normalized similarity into score.
     *
     * @param similarityValue normalized similarity value
     * @return raw score value
     */
    public double getScore(double similarityValue) {
        return score.applyAsDouble(similarityValue);
    }
}
