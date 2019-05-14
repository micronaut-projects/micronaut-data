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

import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.data.intercept.SaveEntityInterceptor;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * A save method for saving a single entity.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SaveMethod extends AbstractPatternBasedMethod implements MethodCandidate {

    private static final String METHOD_PATTERN = "^((save|persist|store|insert)(\\S*?))$";

    /**
     * The default constructor.
     */
    public SaveMethod() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        return parameters.length == 1 && super.isMethodMatch(methodElement, matchContext) && isValidReturnType(matchContext);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        VisitorContext visitorContext = matchContext.getVisitorContext();
        ParameterElement[] parameters = matchContext.getParameters();
        if (ArrayUtils.isNotEmpty(parameters)) {
            if (Arrays.stream(parameters).anyMatch(p -> {
                ClassElement t = p.getGenericType();
                return t != null && t.hasAnnotation(Persisted.class);
            })) {
                return new MethodMatchInfo(matchContext.getReturnType(), null, SaveEntityInterceptor.class);
            }
        }
        visitorContext.fail("Cannot implement save method for specified arguments and return type", matchContext.getMethodElement());
        return null;
    }

    private boolean isValidReturnType(MatchContext matchContext) {
        ClassElement returnType = matchContext.getReturnType();
        ClassElement parameterType = matchContext.getParameters()[0].getGenericType();
        return returnType.hasAnnotation(Persisted.class) && parameterType != null && returnType.getName().equals(parameterType.getName());
    }

}
