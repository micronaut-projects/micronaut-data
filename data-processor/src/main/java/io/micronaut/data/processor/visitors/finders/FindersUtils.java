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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.CountInterceptor;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.DeleteAllInterceptor;
import io.micronaut.data.intercept.DeleteOneInterceptor;
import io.micronaut.data.intercept.ExistsByInterceptor;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.intercept.FindByIdInterceptor;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.intercept.FindOptionalInterceptor;
import io.micronaut.data.intercept.FindPageInterceptor;
import io.micronaut.data.intercept.FindSliceInterceptor;
import io.micronaut.data.intercept.FindStreamInterceptor;
import io.micronaut.data.intercept.SaveAllInterceptor;
import io.micronaut.data.intercept.SaveEntityInterceptor;
import io.micronaut.data.intercept.SaveOneInterceptor;
import io.micronaut.data.intercept.UpdateAllEntitiesInterceptor;
import io.micronaut.data.intercept.UpdateEntityInterceptor;
import io.micronaut.data.intercept.UpdateInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.intercept.async.CountAsyncInterceptor;
import io.micronaut.data.intercept.async.DeleteAllAsyncInterceptor;
import io.micronaut.data.intercept.async.DeleteOneAsyncInterceptor;
import io.micronaut.data.intercept.async.ExistsByAsyncInterceptor;
import io.micronaut.data.intercept.async.FindAllAsyncInterceptor;
import io.micronaut.data.intercept.async.FindByIdAsyncInterceptor;
import io.micronaut.data.intercept.async.FindOneAsyncInterceptor;
import io.micronaut.data.intercept.async.FindPageAsyncInterceptor;
import io.micronaut.data.intercept.async.FindSliceAsyncInterceptor;
import io.micronaut.data.intercept.async.SaveAllAsyncInterceptor;
import io.micronaut.data.intercept.async.SaveEntityAsyncInterceptor;
import io.micronaut.data.intercept.async.SaveOneAsyncInterceptor;
import io.micronaut.data.intercept.async.UpdateAllEntriesAsyncInterceptor;
import io.micronaut.data.intercept.async.UpdateAsyncInterceptor;
import io.micronaut.data.intercept.async.UpdateEntityAsyncInterceptor;
import io.micronaut.data.intercept.reactive.CountReactiveInterceptor;
import io.micronaut.data.intercept.reactive.DeleteAllReactiveInterceptor;
import io.micronaut.data.intercept.reactive.DeleteOneReactiveInterceptor;
import io.micronaut.data.intercept.reactive.ExistsByReactiveInterceptor;
import io.micronaut.data.intercept.reactive.FindAllReactiveInterceptor;
import io.micronaut.data.intercept.reactive.FindByIdReactiveInterceptor;
import io.micronaut.data.intercept.reactive.FindOneReactiveInterceptor;
import io.micronaut.data.intercept.reactive.FindPageReactiveInterceptor;
import io.micronaut.data.intercept.reactive.FindSliceReactiveInterceptor;
import io.micronaut.data.intercept.reactive.SaveAllReactiveInterceptor;
import io.micronaut.data.intercept.reactive.SaveEntityReactiveInterceptor;
import io.micronaut.data.intercept.reactive.SaveOneReactiveInterceptor;
import io.micronaut.data.intercept.reactive.UpdateAllEntitiesReactiveInterceptor;
import io.micronaut.data.intercept.reactive.UpdateEntityReactiveInterceptor;
import io.micronaut.data.intercept.reactive.UpdateReactiveInterceptor;
import io.micronaut.data.model.Slice;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import org.reactivestreams.Publisher;

import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * Finders utils.
 */
@Internal
public interface FindersUtils {

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> resolveInterceptorTypeByOperationType(boolean hasEntityParameter,
                                                                                                           boolean hasMultipleEntityParameter,
                                                                                                           DataMethod.OperationType operationType,
                                                                                                           MethodMatchContext matchContext) {
        ClassElement returnType = matchContext.getMethodElement().getGenericReturnType();
        switch (operationType) {
            case DELETE:
                if (hasEntityParameter) {
                    return pickDeleteInterceptor(matchContext, returnType);
                }
                return pickDeleteAllInterceptor(matchContext, returnType);
            case UPDATE:
                Map.Entry<ClassElement, Class<? extends DataInterceptor>> updateEntry;
                if (hasMultipleEntityParameter) {
                    updateEntry = pickUpdateAllEntitiesInterceptor(matchContext, returnType);
                } else if (hasEntityParameter) {
                    updateEntry = pickUpdateEntityInterceptor(matchContext, returnType);
                } else {
                    updateEntry = pickUpdateInterceptor(matchContext, returnType);
                }
                if (isContainer(updateEntry.getKey(), Iterable.class)) {
                    return typeAndInterceptorEntry(updateEntry.getKey().getFirstTypeArgument().orElseThrow(IllegalStateException::new), updateEntry.getValue());
                }
                return updateEntry;
            case INSERT:
                Map.Entry<ClassElement, Class<? extends DataInterceptor>> saveEntry;
                if (hasEntityParameter) {
                    saveEntry = pickSaveEntityInterceptor(matchContext, returnType);
                } else if (hasMultipleEntityParameter) {
                    saveEntry = pickSaveAllEntitiesInterceptor(matchContext, returnType);
                } else {
                    saveEntry = pickSaveOneInterceptor(matchContext, returnType);
                }
                if (isContainer(saveEntry.getKey(), Iterable.class)) {
                    return typeAndInterceptorEntry(saveEntry.getKey().getFirstTypeArgument().orElseThrow(IllegalStateException::new), saveEntry.getValue());
                }
                return saveEntry;
            case QUERY:
            case COUNT:
            case EXISTS:
                return resolveFindInterceptor(matchContext, returnType);
            default:
                throw new IllegalStateException("Cannot pick interceptor for an operation type: " + operationType + " and a return type: " + returnType);
        }
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickSaveOneInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), SaveOneAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), SaveOneReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), SaveOneInterceptor.class);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickUpdateAllEntitiesInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), UpdateAllEntriesAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), UpdateAllEntitiesReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), UpdateAllEntitiesInterceptor.class);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickDeleteInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), DeleteOneAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), DeleteOneReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), DeleteOneInterceptor.class);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickDeleteAllInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), DeleteAllAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), DeleteAllReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), DeleteAllInterceptor.class);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickSaveEntityInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), SaveEntityAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), SaveEntityReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), SaveEntityInterceptor.class);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickSaveAllEntitiesInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), SaveAllAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), SaveAllReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), SaveAllInterceptor.class);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickUpdateInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), UpdateAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), UpdateReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), UpdateInterceptor.class);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickUpdateEntityInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), UpdateEntityAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), UpdateEntityReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), UpdateEntityInterceptor.class);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> resolveFindInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        Map.Entry<ClassElement, Class<? extends DataInterceptor>> entry;
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            entry = resolveAsyncFindInterceptor(matchContext, getAsyncType(matchContext.getMethodElement(), returnType));
        } else if (isReactiveType(returnType)) {
            entry = resolveReactiveFindInterceptor(matchContext, returnType, returnType.getFirstTypeArgument().orElseThrow(IllegalStateException::new));
        } else {
            entry = resolveSyncFindInterceptor(matchContext, returnType);
        }
//        if (!isValidResultType(entry.getKey())) {
//            matchContext.failAndThrow("Unsupported return type: " + entry.getKey());
//        }
        return entry;
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> resolveSyncFindInterceptor(@NonNull MethodMatchContext matchContext,
                                                                                                @NotNull ClassElement returnType) {
        ClassElement firstTypeArgument = returnType.getFirstTypeArgument().orElse(null);
        if (isPage(matchContext, returnType)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindPageInterceptor.class);
        } else if (isSlice(matchContext, returnType)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindSliceInterceptor.class);
        } else if (isContainer(returnType, Iterable.class)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindAllInterceptor.class);
        } else if (isContainer(returnType, Stream.class)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindStreamInterceptor.class);
        } else if (isContainer(returnType, Optional.class)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindOptionalInterceptor.class);
        } else if (isContainer(returnType, Publisher.class)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindAllReactiveInterceptor.class);
        } else {
            return typeAndInterceptorEntry(returnType, FindOneInterceptor.class);
        }
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> resolveReactiveFindInterceptor(
            @NonNull MethodMatchContext matchContext, @NonNull ClassElement returnType, @NonNull ClassElement reactiveType) {
        ClassElement firstTypeArgument = reactiveType.getFirstTypeArgument().orElse(null);
        if (isPage(matchContext, reactiveType)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindPageReactiveInterceptor.class);
        } else if (isSlice(matchContext, reactiveType)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindSliceReactiveInterceptor.class);
        } else if (isReactiveSingleResult(returnType)) {
            return typeAndInterceptorEntry(reactiveType, FindOneReactiveInterceptor.class);
        } else {
            return typeAndInterceptorEntry(reactiveType, FindAllReactiveInterceptor.class);
        }
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> resolveAsyncFindInterceptor(
            @NonNull MethodMatchContext matchContext, @NonNull ClassElement asyncType) {
        ClassElement firstTypeArgument = asyncType.getFirstTypeArgument().orElse(null);
        if (isPage(matchContext, asyncType)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindPageAsyncInterceptor.class);
        } else if (isSlice(matchContext, asyncType)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindSliceAsyncInterceptor.class);
        } else if (isContainer(asyncType, Iterable.class)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindAllAsyncInterceptor.class);
        } else if (isContainer(asyncType, Optional.class)) {
            return typeAndInterceptorEntry(firstTypeArgument, FindOneAsyncInterceptor.class);
        } else {
            return typeAndInterceptorEntry(asyncType, FindOneAsyncInterceptor.class);
        }
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickCountInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), CountAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), CountReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), CountInterceptor.class);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickExistsInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), ExistsByAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), ExistsByReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), ExistsByInterceptor.class);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickFindByIdInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), FindByIdAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), FindByIdReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), FindByIdInterceptor.class);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> pickFindOneInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType), FindOneAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null), FindOneReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(returnType.getType(), FindOneInterceptor.class);
    }

    static Map.Entry<ClassElement, ClassElement> pickCountSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.CountAsyncSpecificationInterceptor")
            );
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.CountReactiveSpecificationInterceptor")
            );
        }
        return typeAndInterceptorEntry(returnType.getType(),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.CountSpecificationInterceptor")
        );
    }

    static Map.Entry<ClassElement, ClassElement> pickDeleteAllSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.DeleteAllAsyncSpecificationInterceptor")
            );
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.DeleteAllReactiveSpecificationInterceptor")
            );
        }
        return typeAndInterceptorEntry(returnType.getType(),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.DeleteAllSpecificationInterceptor")
        );
    }

    static Map.Entry<ClassElement, ClassElement> pickFindAllSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.FindAllAsyncSpecificationInterceptor")
            );
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.FindAllReactiveSpecificationInterceptor")
            );
        }
        return typeAndInterceptorEntry(returnType.getType(),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.FindAllSpecificationInterceptor")
        );
    }

    static Map.Entry<ClassElement, ClassElement> pickFindOneSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.FindOneAsyncSpecificationInterceptor")
            );
        }
        if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.FindOneReactiveSpecificationInterceptor")
            );
        }
        return typeAndInterceptorEntry(returnType.getType(),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.FindOneSpecificationInterceptor")
        );
    }

    static Map.Entry<ClassElement, ClassElement> pickFindPageSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.FindPageAsyncSpecificationInterceptor")
            );
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getType(),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.FindPageReactiveSpecificationInterceptor")
            );
        }
        return typeAndInterceptorEntry(returnType.getType(),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.FindPageSpecificationInterceptor")
        );
    }

    static Map.Entry<ClassElement, ClassElement> pickUpdateAllSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.UpdateAllAsyncSpecificationInterceptor")
            );
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null),
                    getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.UpdateAllReactiveSpecificationInterceptor")
            );
        }
        return typeAndInterceptorEntry(returnType.getType(),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.UpdateAllSpecificationInterceptor")
        );
    }

    static Map.Entry<ClassElement, ClassElement> pickExistsSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext.getMethodElement(), returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.ExistsAsyncSpecificationInterceptor")
            );
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(null),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.ExistsReactiveSpecificationInterceptor")
            );
        }
        return typeAndInterceptorEntry(returnType.getType(),
            getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.ExistsSpecificationInterceptor")
        );
    }

    static ClassElement getAsyncType(@NonNull MethodElement methodElement,
                                     @NonNull ClassElement returnType) {
        if (methodElement.isSuspend()) {
            return TypeUtils.getKotlinCoroutineProducedType(methodElement);
        }
        return returnType.getFirstTypeArgument().orElse(null);
    }

    static Map.Entry<ClassElement, Class<? extends DataInterceptor>> typeAndInterceptorEntry(ClassElement type, Class<? extends DataInterceptor> interceptor) {
        return new AbstractMap.SimpleEntry<>(type, interceptor);
    }

    static Map.Entry<ClassElement, ClassElement> typeAndInterceptorEntry(ClassElement type, ClassElement interceptor) {
        return new AbstractMap.SimpleEntry<>(type, interceptor);
    }

    static boolean isFutureType(MethodElement methodElement, @Nullable ClassElement type) {
        return methodElement.isSuspend() || isOneOfContainers(type, CompletionStage.class, Future.class);
    }

    static boolean isReactiveType(@Nullable ClassElement type) {
        return isContainer(type, Publisher.class)
                || TypeUtils.isReactiveType(type)
                && (type.getTypeArguments().isEmpty() || isContainer(type, type.getName())); // Validate container argument
    }

    static boolean isPage(MethodMatchContext methodMatchContext, ClassElement typeArgument) {
        boolean matches = methodMatchContext.isTypeInRole(typeArgument, TypeRole.PAGE);
        if (matches && !methodMatchContext.hasParameterInRole(TypeRole.PAGEABLE)) {
            methodMatchContext.fail("Method must accept an argument that is a Pageable");
        }
        return matches;
    }

    static boolean isSlice(MethodMatchContext methodMatchContext, ClassElement typeArgument) {
        boolean matches = methodMatchContext.isTypeInRole(typeArgument, TypeRole.SLICE);
        if (matches && !methodMatchContext.hasParameterInRole(TypeRole.PAGEABLE)) {
            methodMatchContext.fail("Method must accept an argument that is a Pageable");
        }
        return isContainer(typeArgument, Slice.class);
    }

    static boolean isContainer(ClassElement typeArgument, Class<?> containerType) {
        if (typeArgument == null) {
            return false;
        }
        if (typeArgument.isAssignable(containerType)) {
            ClassElement type = typeArgument.getFirstTypeArgument().orElse(null);
            if (type == null) {
                throw new MatchFailedException("'" + containerType + "' return type missing type argument");
            }
            return true;
        }
        return false;
    }

    static boolean isOneOfContainers(ClassElement typeArgument, Class<?>... containers) {
        if (typeArgument == null) {
            return false;
        }
        for (Class<?> containerType : containers) {
            if (isContainer(typeArgument, containerType)) {
                return true;
            }
        }
        return false;
    }

    static boolean isContainer(ClassElement typeArgument, String containerType) {
        if (typeArgument.isAssignable(containerType)) {
            ClassElement type = typeArgument.getFirstTypeArgument().orElse(null);
            if (type == null) {
                throw new MatchFailedException("'" + containerType + "' return type missing type argument");
            }
            return true;
        }
        return false;
    }

    static boolean isValidResultType(ClassElement returnType) {
        return returnType.hasStereotype(Introspected.class) || ClassUtils.isJavaBasicType(returnType.getName()) || returnType.isPrimitive();
    }

    static boolean isReactiveSingleResult(ClassElement returnType) {
        return returnType.hasStereotype(SingleResult.class)
                || isContainer(returnType, "io.reactivex.Single")
                || isContainer(returnType, "reactor.core.publisher.Mono");
    }

    /**
     * Obtain the interceptor element for the given class.
     *
     * @param matchContext The match context
     * @param type         The type
     * @return The element
     */
    static ClassElement getInterceptorElement(@NonNull MethodMatchContext matchContext, Class<? extends DataInterceptor> type) {
        return matchContext.getVisitorContext().getClassElement(type).orElseGet(() -> new FindersUtils.DynamicClassElement(type));
    }

    /**
     * Obtain the interceptor element for the given class name.
     *
     * @param matchContext The match context
     * @param type         The type
     * @return The element
     */
    static ClassElement getInterceptorElement(@NonNull MethodMatchContext matchContext, String type) {
        return matchContext.getVisitorContext().getClassElement(type).orElseThrow(() -> new IllegalStateException("Unable to apply interceptor of type: " + type + ". The interceptor was not found on the classpath. Check your annotation processor configuration and try again."));
    }

    /**
     * Internally used for dynamically defining a class element.
     */
    class DynamicClassElement implements ClassElement {
        private final Class<? extends DataInterceptor> type;

        DynamicClassElement(Class<? extends DataInterceptor> type) {
            this.type = type;
        }

        @Override
        public boolean isAssignable(String type) {
            return false;
        }

        @Override
        public ClassElement toArray() {
            return new DynamicClassElement((Class<? extends DataInterceptor>) Array.newInstance(type, 0).getClass());
        }

        @Override
        public ClassElement fromArray() {
            return new DynamicClassElement((Class<? extends DataInterceptor>) type.getComponentType());
        }

        @NonNull
        @Override
        public String getName() {
            return type.getName();
        }

        @Override
        public boolean isProtected() {
            return Modifier.isProtected(type.getModifiers());
        }

        @Override
        public boolean isPublic() {
            return Modifier.isPublic(type.getModifiers());
        }

        @NonNull
        @Override
        public Object getNativeType() {
            return type;
        }
    }
}
