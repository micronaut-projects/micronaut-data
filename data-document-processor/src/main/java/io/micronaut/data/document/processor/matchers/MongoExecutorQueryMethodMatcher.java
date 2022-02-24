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
package io.micronaut.data.document.processor.matchers;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.document.mongo.MongoAnnotations;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.MethodMatcher;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.Map;
import java.util.Optional;

/**
 * Finder with `MongoQueryExecutor` repository implementation.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
public class MongoExecutorQueryMethodMatcher implements MethodMatcher {

    /**
     * Default constructor.
     */
    public MongoExecutorQueryMethodMatcher() {
    }

    @Override
    public final int getOrder() {
        // should run first and before `MongoExecutorQueryMethodMatcher`
        return DEFAULT_POSITION - 2001;
    }

    @Override
    public MethodMatch match(MethodMatchContext matchContext) {
        Optional<ClassElement> executor = matchContext.getVisitorContext().getClassElement(MongoAnnotations.EXECUTOR_REPOSITORY);
        if (executor.isPresent() && executor.get().isAssignable(matchContext.getRepositoryClass())) {
            return null;
        }
        String methodName = matchContext.getMethodElement().getName();
        if ("findAll".equals(methodName) || "findOne".equals(methodName)) {
            ParameterElement[] parameters = matchContext.getParameters();
            switch (parameters.length) {
                case 1:
                    ParameterElement parameter = parameters[0];
                    if (isBson(parameter)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.QUERY) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(MongoAnnotations.FILTER_ROLE, parameter.getName());
                            }

                        };
                    } else if (isPipeline(parameter)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.QUERY) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(MongoAnnotations.PIPELINE_ROLE, parameter.getName());
                            }

                        };
                    } else if (parameter.getType().isAssignable(MongoAnnotations.FIND_OPTIONS_BEAN)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.QUERY) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(MongoAnnotations.FIND_OPTIONS_ROLE, parameter.getName());
                            }

                        };
                    }
                    break;
                case 2:
                    ParameterElement parameter1 = parameters[0];
                    ParameterElement parameter2 = parameters[1];
                    if (isBson(parameter1) && parameter2.getType().isAssignable(MongoAnnotations.FIND_OPTIONS_BEAN)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.QUERY) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(MongoAnnotations.FILTER_ROLE, parameter1.getName());
                                matchInfo.addParameterRole(MongoAnnotations.FIND_OPTIONS_ROLE, parameter2.getName());
                            }

                        };
                    } else if (isPipeline(parameter1) && parameter2.getType().isAssignable(MongoAnnotations.AGGREGATION_OPTIONS_BEAN)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.QUERY) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(MongoAnnotations.PIPELINE_ROLE, parameter1.getName());
                                matchInfo.addParameterRole(MongoAnnotations.AGGREGATE_OPTIONS_ROLE, parameter2.getName());
                            }

                        };
                    }
                    if ("findOne".equals(methodName)) {
                        break;
                    }
                    ParameterElement p1 = parameters[0];
                    ParameterElement p2 = parameters[1];
                    if (isBson(p1)
                            && p2.getType().isAssignable(MongoAnnotations.PAGEABLE_BEAN)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.QUERY) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(MongoAnnotations.FILTER_ROLE, p1.getName());
                                matchInfo.addParameterRole(TypeRole.PAGEABLE, p2.getName());
                                // Fake query to have stored query
                                matchContext.getMethodElement().annotate(Query.class, builder -> {
                                    builder.member(DataMethod.META_MEMBER_COUNT_QUERY, "{}");
                                });
                            }

                        };
                    } else if (p1.getType().isAssignable(MongoAnnotations.FIND_OPTIONS_BEAN)
                            && p2.getType().isAssignable(MongoAnnotations.PAGEABLE_BEAN)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.QUERY) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(MongoAnnotations.FIND_OPTIONS_ROLE, p1.getName());
                                matchInfo.addParameterRole(TypeRole.PAGEABLE, p2.getName());
                                // Fake query to have stored query
                                matchContext.getMethodElement().annotate(Query  .class, builder -> {
                                    builder.member(DataMethod.META_MEMBER_COUNT_QUERY, "{}");
                                });
                            }

                        };
                    }
                    break;
                default:
                    return null;
            }
        }
        if ("count".equals(methodName)) {
            ParameterElement[] parameters = matchContext.getParameters();
            if (parameters.length == 1) {
                ParameterElement parameter = parameters[0];
                if (isBson(parameter)) {
                    return new MongoQueryExecutorMatch(DataMethod.OperationType.COUNT) {

                        @Override
                        protected void apply(MethodMatchInfo matchInfo) {
                            matchInfo.addParameterRole(MongoAnnotations.FILTER_ROLE, parameter.getName());
                        }

                    };
                }
            }
            return null;
        }
        if ("deleteAll".equals(methodName)) {
            ParameterElement[] parameters = matchContext.getParameters();
            switch (parameters.length) {
                case 1:
                    ParameterElement parameter = parameters[0];
                    if (isBson(parameter)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.DELETE) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(MongoAnnotations.FILTER_ROLE, parameter.getName());
                            }

                        };
                    }
                    break;
                case 2:
                    ParameterElement parameter1 = parameters[0];
                    ParameterElement parameter2 = parameters[1];
                    if (isBson(parameter1) && parameter2.getType().isAssignable(MongoAnnotations.DELETE_OPTIONS_BEAN)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.DELETE) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(MongoAnnotations.FILTER_ROLE, parameter1.getName());
                                matchInfo.addParameterRole(MongoAnnotations.DELETE_OPTIONS_ROLE, parameter2.getName());
                            }

                        };
                    }
                    break;
                default:
                    return null;
            }
        }
        if ("updateAll".equals(methodName)) {
            ParameterElement[] parameters = matchContext.getParameters();
            switch (parameters.length) {
                case 2:
                    ParameterElement parameter1 = parameters[0];
                    ParameterElement parameter2 = parameters[1];
                    if (isBson(parameter1) && isBson(parameter2)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.UPDATE) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(MongoAnnotations.FILTER_ROLE, parameter1.getName());
                                matchInfo.addParameterRole(MongoAnnotations.UPDATE_ROLE, parameter2.getName());
                            }

                        };
                    }
                    break;
                case 3:
                    ParameterElement filter = parameters[0];
                    ParameterElement update = parameters[1];
                    ParameterElement options = parameters[2];
                    if (isBson(filter) && isBson(update) && options.getType().isAssignable(MongoAnnotations.UPDATE_OPTIONS_BEAN)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.UPDATE) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(MongoAnnotations.FILTER_ROLE, filter.getName());
                                matchInfo.addParameterRole(MongoAnnotations.UPDATE_ROLE, update.getName());
                                matchInfo.addParameterRole(MongoAnnotations.UPDATE_OPTIONS_ROLE, options.getName());
                            }

                        };
                    }
                    break;
                default:
                    return null;
            }
        }
        return null;
    }

    private boolean isPipeline(ParameterElement parameter) {
        if (!parameter.getType().isAssignable(Iterable.class)) {
            return false;
        }
        Optional<ClassElement> firstTypeArgument = parameter.getType().getFirstTypeArgument();
        return firstTypeArgument.isPresent() && firstTypeArgument.get().isAssignable(MongoAnnotations.BSON);
    }

    private boolean isBson(ParameterElement parameter) {
        return parameter.getType().isAssignable(MongoAnnotations.BSON);
    }

    private abstract static class MongoQueryExecutorMatch implements MethodMatch {
        private final DataMethod.OperationType operationType;

        public MongoQueryExecutorMatch(DataMethod.OperationType operationType) {
            this.operationType = operationType;
        }

        protected abstract void apply(MethodMatchInfo matchInfo);

        @Override
        public MethodMatchInfo buildMatchInfo(MethodMatchContext matchContext) {
            Map.Entry<ClassElement, Class<? extends DataInterceptor>> entry = FindersUtils.resolveInterceptorTypeByOperationType(
                    false,
                    false,
                    operationType,
                    matchContext);
            MethodMatchInfo methodMatchInfo = new MethodMatchInfo(
                    operationType,
                    entry.getKey(),
                    FindersUtils.getInterceptorElement(matchContext, entry.getValue())
            );
            // Fake query to have stored query
            matchContext.getMethodElement().annotate(Query.class, builder -> {
                builder.value("{}");
                if (operationType == DataMethod.OperationType.UPDATE) {
                    builder.member("update", "{}");
                }
            });
            apply(methodMatchInfo);
            return methodMatchInfo;
        }
    }
}
