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
package io.micronaut.data.model.jpa.criteria.impl.predicate;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import jakarta.persistence.criteria.Expression;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB full-text search predicate.
 *
 * @since 5.0.0
 */
@Internal
public final class TextPredicate extends AbstractPredicate {

    private final Expression<String> search;
    @Nullable
    private final Expression<String> language;
    @Nullable
    private final Expression<Boolean> caseSensitive;
    @Nullable
    private final Expression<Boolean> diacriticSensitive;

    public TextPredicate(Expression<String> search,
                         @Nullable Expression<String> language,
                         @Nullable Expression<Boolean> caseSensitive,
                         @Nullable Expression<Boolean> diacriticSensitive) {
        this.search = CriteriaUtils.requireStringExpression(search);
        this.language = language == null ? null : CriteriaUtils.requireStringExpression(language);
        this.caseSensitive = caseSensitive == null ? null : CriteriaUtils.requireBoolExpression(caseSensitive);
        this.diacriticSensitive = diacriticSensitive == null ? null : CriteriaUtils.requireBoolExpression(diacriticSensitive);
    }

    public Expression<String> getSearch() {
        return search;
    }

    public @Nullable Expression<String> getLanguage() {
        return language;
    }

    public @Nullable Expression<Boolean> getCaseSensitive() {
        return caseSensitive;
    }

    public @Nullable Expression<Boolean> getDiacriticSensitive() {
        return diacriticSensitive;
    }

    @Override
    public void visitPredicate(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }
}
