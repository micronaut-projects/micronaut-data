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

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.sql.Procedure;
import io.micronaut.data.intercept.ProcedureInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.intercept.async.ProcedureAsyncInterceptor;
import io.micronaut.data.intercept.reactive.ProcedureReactiveInterceptor;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.BindingParameter.BindingContext;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.processor.model.criteria.impl.SourceParameterExpressionImpl;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.Utils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Finder for the procedure methods.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
public final class ProcedureMethodMatcher implements MethodMatcher {

    /**
     * Default constructor.
     */
    public ProcedureMethodMatcher() {
    }

    @Override
    public int getOrder() {
        // should run first
        return DEFAULT_POSITION - 1000;
    }

    @Override
    public MethodMatch match(MethodMatchContext matchContext) {
        AnnotationValue<Procedure> procedureAnnotationValue = matchContext.getMethodElement().getAnnotation(Procedure.class);
        if (procedureAnnotationValue != null) {
            return new MethodMatch() {

                @Override
                public MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext) {
                    ClassElement resultType;
                    ClassElement interceptor;
                    MethodElement methodElement = matchContext.getMethodElement();
                    ClassElement returnType = methodElement.getReturnType();
                    if (FindersUtils.isFutureType(methodElement, returnType)) {
                        interceptor = matchContext.getVisitorContext().getClassElement(ProcedureAsyncInterceptor.class).orElseThrow();
                        resultType = FindersUtils.getAsyncType(methodElement, returnType);
                    } else if (FindersUtils.isReactiveType(returnType)) {
                        interceptor = matchContext.getVisitorContext().getClassElement(ProcedureReactiveInterceptor.class).orElseThrow();
                        resultType = returnType.getFirstTypeArgument().orElse(returnType);
                    } else {
                        interceptor = matchContext.getVisitorContext().getClassElement(ProcedureInterceptor.class).orElseThrow();
                        resultType = matchContext.getReturnType();
                    }

                    MethodMatchInfo methodMatchInfo = new MethodMatchInfo(
                        DataMethod.OperationType.QUERY,
                        resultType,
                        interceptor
                    );

                    QueryResult queryResult = getQueryResult(matchContext, procedureAnnotationValue, matchContext.getParametersNotInRole());
                    methodMatchInfo.queryResult(queryResult);

                    return methodMatchInfo;
                }
            };
        }
        return null;
    }

    private QueryResult getQueryResult(MethodMatchContext matchContext,
                                       AnnotationValue<Procedure> procedureAnnotationValue,
                                       List<ParameterElement> parameters) {
        boolean namedParameters = matchContext.getRepositoryClass()
            .booleanValue(RepositoryConfiguration.class, "namedParameters").orElse(true);

        List<QueryParameterBinding> parameterBindings = new ArrayList<>(parameters.size());
        int index = 1;

        for (ParameterElement parameter : parameters) {
            String name = parameter.stringValue(Parameter.class).orElse(parameter.getName());
            PersistentPropertyPath propertyPath = matchContext.getRootEntity().getPropertyPath(name);
            BindingContext bindingContext = BindingContext.create()
                .incomingMethodParameterProperty(propertyPath)
                .outgoingQueryParameterProperty(propertyPath);

            if (namedParameters) {
                bindingContext = bindingContext.name(name);
            } else {
                bindingContext = bindingContext.index(index++);
            }

            parameterBindings.add(bindingParameter(matchContext, parameter).bind(bindingContext));
        }

        String query;
        Optional<String> named = procedureAnnotationValue.stringValue("named");
        if (named.isPresent()) {
            query = "";
        } else {
            String procedureName = procedureAnnotationValue.stringValue().orElseGet(() -> matchContext.getMethodElement().getName());
            int parametersSize = parameters.size();
            if (!matchContext.getMethodElement().getReturnType().isVoid()) {
                parametersSize++;
            }
            query = "CALL " + procedureName + "(" + IntStream.range(0, parametersSize).mapToObj(ignore -> "?").collect(Collectors.joining(",")) + ")";
        }

        return new QueryResult() {
            @Override
            public String getQuery() {
                return query;
            }

            @Override
            public List<String> getQueryParts() {
                return List.of();
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return parameterBindings;
            }

            @Override
            public Map<String, String> getAdditionalRequiredParameters() {
                return Collections.emptyMap();
            }
        };
    }

    private SourceParameterExpressionImpl bindingParameter(MethodMatchContext matchContext, ParameterElement element) {
        return bindingParameter(matchContext, element, false);
    }

    private SourceParameterExpressionImpl bindingParameter(MethodMatchContext matchContext, ParameterElement element, boolean isEntityParameter) {
        return new SourceParameterExpressionImpl(
            Utils.getConfiguredDataTypes(matchContext.getRepositoryClass()),
            matchContext.getParameters(),
            element,
            isEntityParameter);
    }

}
