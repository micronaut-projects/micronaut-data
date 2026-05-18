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
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.FindersUtils;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.data.processor.visitors.finders.MethodMatcher;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Finder with `MongoQueryExecutor` repository implementation.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
public class MongoExecutorQueryMethodMatcher implements MethodMatcher {

    @Override
    public final int getOrder() {
        // should run first and before `MongoExecutorQueryMethodMatcher`
        return DEFAULT_POSITION - 2001;
    }

    @Override
    @Nullable
    public MethodMatch match(MethodMatchContext matchContext) {
        Optional<ClassElement> executor = matchContext.getVisitorContext().getClassElement(MongoAnnotations.EXECUTOR_REPOSITORY);
        if (executor.isPresent() && executor.get().isAssignable(matchContext.getRepositoryClass())) {
            return null;
        }
        Optional<ClassElement> reactiveExecutor = matchContext.getVisitorContext().getClassElement(MongoAnnotations.REACTIVE_EXECUTOR_REPOSITORY);
        if (reactiveExecutor.isPresent() && reactiveExecutor.get().isAssignable(matchContext.getRepositoryClass())) {
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
                                matchInfo.addParameterRole(parameter, MongoAnnotations.FILTER_ROLE);
                            }

                        };
                    } else if (isPipeline(parameter)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.QUERY) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(parameter, MongoAnnotations.PIPELINE_ROLE);
                            }

                        };
                    } else if (parameter.getType().isAssignable(MongoAnnotations.FIND_OPTIONS_BEAN)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.QUERY) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(parameter, MongoAnnotations.FIND_OPTIONS_ROLE);
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
                                matchInfo.addParameterRole(parameter1, MongoAnnotations.FILTER_ROLE);
                                matchInfo.addParameterRole(parameter2, MongoAnnotations.FIND_OPTIONS_ROLE);
                            }

                        };
                    } else if (isPipeline(parameter1) && parameter2.getType().isAssignable(MongoAnnotations.AGGREGATION_OPTIONS_BEAN)) {
                        return new MongoQueryExecutorMatch(DataMethod.OperationType.QUERY) {

                            @Override
                            protected void apply(MethodMatchInfo matchInfo) {
                                matchInfo.addParameterRole(parameter1, MongoAnnotations.PIPELINE_ROLE);
                                matchInfo.addParameterRole(parameter2, MongoAnnotations.AGGREGATE_OPTIONS_ROLE);
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
                                matchInfo.addParameterRole(p1, MongoAnnotations.FILTER_ROLE);
                                matchInfo.addParameterRole(p2, TypeRole.PAGEABLE);
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
                                matchInfo.addParameterRole(p1, MongoAnnotations.FIND_OPTIONS_ROLE);
                                matchInfo.addParameterRole(p2, TypeRole.PAGEABLE);
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
                            matchInfo.addParameterRole(parameter, MongoAnnotations.FILTER_ROLE);
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
                                matchInfo.addParameterRole(parameter, MongoAnnotations.FILTER_ROLE);
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
                                matchInfo.addParameterRole(parameter1, MongoAnnotations.FILTER_ROLE);
                                matchInfo.addParameterRole(parameter2, MongoAnnotations.DELETE_OPTIONS_ROLE);
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
                                matchInfo.addParameterRole(parameter1, MongoAnnotations.FILTER_ROLE);
                                matchInfo.addParameterRole(parameter2, MongoAnnotations.UPDATE_ROLE);
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
                                matchInfo.addParameterRole(filter, MongoAnnotations.FILTER_ROLE);
                                matchInfo.addParameterRole(update, MongoAnnotations.UPDATE_ROLE);
                                matchInfo.addParameterRole(options, MongoAnnotations.UPDATE_OPTIONS_ROLE);
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
            FindersUtils.InterceptorMatch entry = FindersUtils.resolveInterceptorTypeByOperationType(
                    false,
                    false,
                    operationType,
                    matchContext);
            MethodMatchInfo methodMatchInfo = new MethodMatchInfo(
                    operationType,
                    entry.returnType(),
                    entry.interceptor()
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
