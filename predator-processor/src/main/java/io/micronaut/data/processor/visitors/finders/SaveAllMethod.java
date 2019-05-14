/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.intercept.SaveAllInterceptor;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.regex.Pattern;

/**
 * A save all method for saving several entities.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SaveAllMethod extends AbstractPatternBasedMethod {

    private static final String METHOD_PATTERN = "^((save|persist|store|insert)(\\S*?))$";

    /**
     * Default constructor.
     */
    public SaveAllMethod() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        ParameterElement[] parameters = methodElement.getParameters();
        if(parameters.length == 1) {
            ParameterElement firstParameter = parameters[0];
            return super.isMethodMatch(methodElement, matchContext) && TypeUtils.isIterableOfEntity(firstParameter.getGenericType());
        }
        return false;
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        // default doesn't build a query and query construction left to runtime
        // this is fine for JPA, for SQL we need to build an insert

        return new MethodMatchInfo(
                null,
                null,
                SaveAllInterceptor.class
        );
    }
}
