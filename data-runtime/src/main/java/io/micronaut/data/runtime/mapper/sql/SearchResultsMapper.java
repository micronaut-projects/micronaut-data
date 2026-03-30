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
package io.micronaut.data.runtime.mapper.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.ScoringFunction;
import io.micronaut.data.model.vector.search.SearchResult;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.model.vector.search.Similarity;
import io.micronaut.data.runtime.operations.internal.sql.SimilarityNormalizer;
import io.micronaut.data.runtime.mapper.ResultReader;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal row mapper that composes entity payloads with vector score/similarity metadata.
 *
 * <p>The mapper expects the SQL projection to include a score column identified by {@code scoreAlias}.
 * When a {@link ScoringFunction} is provided, normalized similarity is derived via
 * {@link SimilarityNormalizer}; otherwise similarity is omitted.</p>
 *
 * @param <RS> native row/result-set type
 * @param <E> mapped entity type
 * @since 5.0.0
 */
@Internal
public final class SearchResultsMapper<RS, E> {

    private final SqlTypeMapper<RS, E> entityMapper;
    private final ResultReader<RS, String> resultReader;
    private final String scoreAlias;
    @Nullable
    private final ScoringFunction scoringFunction;

    /**
     * @param entityMapper Entity mapper used to map each row entity payload
     * @param resultReader Reader used to extract score alias values
     * @param scoreAlias Result column alias containing the score value
     */
    public SearchResultsMapper(SqlTypeMapper<RS, E> entityMapper,
                               ResultReader<RS, String> resultReader,
                               String scoreAlias) {
        this(entityMapper, resultReader, scoreAlias, null);
    }

    /**
     * @param entityMapper Entity mapper used to map each row entity payload
     * @param resultReader Reader used to extract score alias values
     * @param scoreAlias Result column alias containing the score value
     * @param scoringFunction Optional scoring function used to compute normalized similarity
     */
    public SearchResultsMapper(SqlTypeMapper<RS, E> entityMapper,
                               ResultReader<RS, String> resultReader,
                               String scoreAlias,
                               @Nullable ScoringFunction scoringFunction) {
        this.entityMapper = entityMapper;
        this.resultReader = resultReader;
        this.scoreAlias = scoreAlias;
        this.scoringFunction = scoringFunction;
    }

    /**
     * Maps all rows from the provided result set into {@link SearchResults}.
     *
     * @param rs Result set/row stream handle
     * @param entityType Entity type to map
     * @return Mapped search results
     */
    public SearchResults<E> mapAll(RS rs, Class<E> entityType) {
        List<SearchResult<E>> out = new ArrayList<>();
        while (hasNext(rs)) {
            SearchResult<E> searchResult = mapOne(rs, entityType);
            if (searchResult != null) {
                out.add(searchResult);
            }
        }
        return new SearchResults<>(out);
    }

    /**
     * @param rs Result set/row stream handle
     * @return Whether another row is available
     */
    public boolean hasNext(RS rs) {
        return entityMapper.hasNext(rs);
    }

    /**
     * Maps the current row into a {@link SearchResult}.
     *
     * @param rs Result set/row stream handle
     * @param entityType Entity type to map
     * @return mapped result or {@code null} when entity mapping yields no row data
     */
    public @Nullable SearchResult<E> mapOne(RS rs, Class<E> entityType) {
        E entity = entityMapper.map(rs, entityType);
        if (entity == null) {
            return null;
        }
        Double scoreValue = resultReader.getRequiredValue(rs, scoreAlias, Double.class);
        if (scoreValue == null) {
            scoreValue = 0d;
        }
        Score score = new Score(scoreValue);
        Similarity similarity = scoringFunction == null ? null : new Similarity(
            SimilarityNormalizer.forScoringFunction(scoringFunction).getSimilarity(scoreValue)
        );
        return new SearchResult<>(entity, score, similarity);
    }
}
