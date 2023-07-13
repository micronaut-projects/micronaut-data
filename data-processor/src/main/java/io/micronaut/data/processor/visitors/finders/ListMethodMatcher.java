/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.criteria.QueryCriteriaMethodMatch;

/**
 * List method matcher.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class ListMethodMatcher extends AbstractPatternMethodMatcher {

    public ListMethodMatcher() {
        super(false, "list", "find", "search", "query");
    }

    @Override
    protected MethodMatch match(MethodMatchContext matchContext, java.util.regex.Matcher matcher) {
        if (TypeUtils.isContainerType(matchContext.getReturnType()) || FindersUtils.isFindAllCompatibleReturnType(matchContext)) {
            return new QueryCriteriaMethodMatch(matcher);
        }
        return null;
    }

    @Override
    public int getOrder() {
        // lower priority than dynamic finder
        return DEFAULT_POSITION + 100;
    }

}
