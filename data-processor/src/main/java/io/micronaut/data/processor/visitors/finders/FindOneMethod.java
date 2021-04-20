/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.inject.ast.MethodElement;

/**
 * Simple list method support.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class FindOneMethod extends AbstractListMethod {

    /**
     * The prefixes used.
     */
    public static final String[] PREFIXES = {"get", "find", "search", "query"};

    /**
     * Find one method.
     */
    public FindOneMethod() {
        super(PREFIXES);
    }

    @Override
    public int getOrder() {
        // lower priority than dynamic finder
        return DEFAULT_POSITION + 100;
    }

    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext) &&
                        matchContext.getReturnType().hasStereotype(Introspected.class);
    }
}
