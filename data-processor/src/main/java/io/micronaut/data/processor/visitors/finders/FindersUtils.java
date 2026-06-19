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
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.CountInterceptor;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.intercept.DeleteAllInterceptor;
import io.micronaut.data.intercept.DeleteAllReturningInterceptor;
import io.micronaut.data.intercept.DeleteOneInterceptor;
import io.micronaut.data.intercept.DeleteReturningManyInterceptor;
import io.micronaut.data.intercept.DeleteReturningOneInterceptor;
import io.micronaut.data.intercept.ExistsByInterceptor;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.intercept.FindByIdInterceptor;
import io.micronaut.data.intercept.FindCursoredPageInterceptor;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.intercept.FindOptionalInterceptor;
import io.micronaut.data.intercept.FindPageInterceptor;
import io.micronaut.data.intercept.FindSliceInterceptor;
import io.micronaut.data.intercept.FindStreamInterceptor;
import io.micronaut.data.intercept.InsertAllInterceptor;
import io.micronaut.data.intercept.InsertEntityInterceptor;
import io.micronaut.data.intercept.InsertOneInterceptor;
import io.micronaut.data.intercept.InsertReturningManyInterceptor;
import io.micronaut.data.intercept.InsertReturningOneInterceptor;
import io.micronaut.data.intercept.ProcedureReturningManyInterceptor;
import io.micronaut.data.intercept.ProcedureReturningOneInterceptor;
import io.micronaut.data.intercept.SaveAllInterceptor;
import io.micronaut.data.intercept.SaveEntityInterceptor;
import io.micronaut.data.intercept.SaveOneInterceptor;
import io.micronaut.data.intercept.UpdateAllEntitiesInterceptor;
import io.micronaut.data.intercept.UpdateEntityInterceptor;
import io.micronaut.data.intercept.UpdateInterceptor;
import io.micronaut.data.intercept.UpdateReturningManyInterceptor;
import io.micronaut.data.intercept.UpdateReturningOneInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.intercept.async.CountAsyncInterceptor;
import io.micronaut.data.intercept.async.DeleteAllAsyncInterceptor;
import io.micronaut.data.intercept.async.DeleteOneAsyncInterceptor;
import io.micronaut.data.intercept.async.ExistsByAsyncInterceptor;
import io.micronaut.data.intercept.async.FindAllAsyncInterceptor;
import io.micronaut.data.intercept.async.FindByIdAsyncInterceptor;
import io.micronaut.data.intercept.async.FindOneAsyncInterceptor;
import io.micronaut.data.intercept.async.FindPageAsyncInterceptor;
import io.micronaut.data.intercept.async.FindCursoredAsyncPageInterceptor;
import io.micronaut.data.intercept.async.FindSliceAsyncInterceptor;
import io.micronaut.data.intercept.async.DeleteReturningManyAsyncInterceptor;
import io.micronaut.data.intercept.async.DeleteReturningOneAsyncInterceptor;
import io.micronaut.data.intercept.async.InsertAllAsyncInterceptor;
import io.micronaut.data.intercept.async.InsertEntityAsyncInterceptor;
import io.micronaut.data.intercept.async.InsertOneAsyncInterceptor;
import io.micronaut.data.intercept.async.InsertReturningManyAsyncInterceptor;
import io.micronaut.data.intercept.async.InsertReturningOneAsyncInterceptor;
import io.micronaut.data.intercept.async.ProcedureReturningManyAsyncInterceptor;
import io.micronaut.data.intercept.async.ProcedureReturningOneAsyncInterceptor;
import io.micronaut.data.intercept.async.SaveAllAsyncInterceptor;
import io.micronaut.data.intercept.async.UpdateReturningManyAsyncInterceptor;
import io.micronaut.data.intercept.async.UpdateReturningOneAsyncInterceptor;
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
import io.micronaut.data.intercept.reactive.FindCursoredReactivePageInterceptor;
import io.micronaut.data.intercept.reactive.FindSliceReactiveInterceptor;
import io.micronaut.data.intercept.reactive.InsertAllReactiveInterceptor;
import io.micronaut.data.intercept.reactive.InsertEntityReactiveInterceptor;
import io.micronaut.data.intercept.reactive.InsertOneReactiveInterceptor;
import io.micronaut.data.intercept.reactive.ProcedureReactiveInterceptor;
import io.micronaut.data.intercept.reactive.SaveAllReactiveInterceptor;
import io.micronaut.data.intercept.reactive.SaveEntityReactiveInterceptor;
import io.micronaut.data.intercept.reactive.SaveOneReactiveInterceptor;
import io.micronaut.data.intercept.reactive.UpdateAllEntitiesReactiveInterceptor;
import io.micronaut.data.intercept.reactive.UpdateEntityReactiveInterceptor;
import io.micronaut.data.intercept.reactive.UpdateReactiveInterceptor;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.processor.visitors.FindInterceptorDef;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Finders utils.
 */
@Internal
public interface FindersUtils {

    static List<FindInterceptorDef> getDefaultInterceptors(VisitorContext visitorContext) {
        return List.of(
            new FindInterceptorDef(
                visitorContext.getClassElement(Stream.class).orElseThrow(),
                visitorContext.getClassElement(FindStreamInterceptor.class).orElseThrow()
            ),
            new FindInterceptorDef(
                visitorContext.getClassElement(Optional.class).orElseThrow(),
                visitorContext.getClassElement(FindOptionalInterceptor.class).orElseThrow()
            )
        );
    }

    static FindersUtils.InterceptorMatch resolveInterceptorTypeByOperationType(boolean hasEntityParameter,
                                                                               boolean hasMultipleEntityParameter,
                                                                               DataMethod.OperationType operationType,
                                                                               MethodMatchContext matchContext) {
        ClassElement returnType = matchContext.getMethodElement().getGenericReturnType();
        return switch (operationType) {
            case DELETE -> {
                if (hasEntityParameter) {
                    yield pickDeleteInterceptor(matchContext, returnType);
                } else {
                    yield pickDeleteAllInterceptor(matchContext, returnType);
                }
            }
            case DELETE_RETURNING -> {
                boolean returnsEntity = TypeUtils.doesMethodProducesAnEntityIterableOfAnEntity(matchContext.getMethodElement());
                InterceptorMatch updateEntry;
                if (hasEntityParameter && returnsEntity) {
                    if (isReactiveType(returnType) || isFutureType(matchContext.getMethodElement(), returnType)) {
                        updateEntry = pickDeleteReturningInterceptor(matchContext, returnType);
                    } else {
                        updateEntry = pickDeleteInterceptor(matchContext, returnType);
                    }
                } else if (hasMultipleEntityParameter && returnsEntity) {
                    if (isReactiveType(returnType) || isFutureType(matchContext.getMethodElement(), returnType)) {
                        updateEntry = pickDeleteReturningInterceptor(matchContext, returnType);
                    } else {
                        updateEntry = pickDeleteAllReturningInterceptor(matchContext, returnType);
                    }
                } else {
                    updateEntry = pickDeleteReturningInterceptor(matchContext, returnType);
                }
                if (isContainer(updateEntry.returnType, Iterable.class)) {
                    yield typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, updateEntry.returnType), updateEntry.interceptor);
                } else {
                    yield updateEntry;
                }
            }
            case UPDATE, UPSERT -> {
                InterceptorMatch updateEntry;
                if (hasMultipleEntityParameter) {
                    updateEntry = pickUpdateAllEntitiesInterceptor(matchContext, returnType);
                } else if (hasEntityParameter) {
                    updateEntry = pickUpdateEntityInterceptor(matchContext, returnType);
                } else {
                    updateEntry = pickUpdateInterceptor(matchContext, returnType);
                }
                if (isContainer(updateEntry.returnType, Iterable.class)) {
                    yield typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, updateEntry.returnType), updateEntry.interceptor);
                } else {
                    yield updateEntry;
                }
            }
            case UPDATE_RETURNING -> {
                boolean returnsEntity = TypeUtils.doesMethodProducesAnEntityIterableOfAnEntity(matchContext.getMethodElement());
                InterceptorMatch updateEntry;
                if (hasMultipleEntityParameter && returnsEntity) {
                    updateEntry = pickUpdateAllEntitiesInterceptor(matchContext, returnType);
                } else if (hasEntityParameter && returnsEntity) {
                    if (isReactiveType(returnType) || isFutureType(matchContext.getMethodElement(), returnType)) {
                        updateEntry = pickUpdateReturningInterceptor(matchContext, returnType);
                    } else {
                        updateEntry = pickUpdateEntityInterceptor(matchContext, returnType);
                    }
                } else {
                    updateEntry = pickUpdateReturningInterceptor(matchContext, returnType);
                }
                if (isContainer(updateEntry.returnType, Iterable.class)) {
                    yield typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, updateEntry.returnType), updateEntry.interceptor);
                } else {
                    yield updateEntry;
                }
            }
            case INSERT -> {
                InterceptorMatch saveEntry;
                if (hasEntityParameter) {
                    saveEntry = pickInsertEntityInterceptor(matchContext, returnType);
                } else if (hasMultipleEntityParameter) {
                    saveEntry = pickInsertAllEntitiesInterceptor(matchContext, returnType);
                } else {
                    saveEntry = pickInsertOneInterceptor(matchContext, returnType);
                }
                if (isContainer(saveEntry.returnType, Iterable.class)) {
                    yield typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, saveEntry.returnType), saveEntry.interceptor);
                } else {
                    yield saveEntry;
                }
            }
            case INSERT_RETURNING -> {
                boolean returnsEntity = TypeUtils.doesMethodProducesAnEntityIterableOfAnEntity(matchContext.getMethodElement());
                InterceptorMatch saveEntry;
                if (hasEntityParameter && returnsEntity) {
                    saveEntry = pickInsertEntityInterceptor(matchContext, returnType);
                } else if (hasMultipleEntityParameter && returnsEntity) {
                    saveEntry = pickInsertAllEntitiesInterceptor(matchContext, returnType);
                } else {
                    saveEntry = pickInsertReturningInterceptor(matchContext, returnType);
                }
                if (isContainer(saveEntry.returnType, Iterable.class)) {
                    yield typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, saveEntry.returnType), saveEntry.interceptor);
                } else {
                    yield saveEntry;
                }
            }
            case QUERY, COUNT, EXISTS -> resolveFindInterceptor(matchContext, returnType);
        };
    }

    static FindersUtils.InterceptorMatch resolveSaveInterceptorType(boolean hasEntityParameter,
                                                                    boolean hasMultipleEntityParameter,
                                                                    MethodMatchContext matchContext) {
        ClassElement returnType = matchContext.getMethodElement().getGenericReturnType();
        InterceptorMatch saveEntry;
        if (hasEntityParameter) {
            saveEntry = pickSaveEntityInterceptor(matchContext, returnType);
        } else if (hasMultipleEntityParameter) {
            saveEntry = pickSaveAllEntitiesInterceptor(matchContext, returnType);
        } else {
            saveEntry = pickSaveOneInterceptor(matchContext, returnType);
        }
        if (isContainer(saveEntry.returnType, Iterable.class)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, saveEntry.returnType), saveEntry.interceptor);
        }
        return saveEntry;
    }

    private static InterceptorMatch pickUpdateReturningInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            ClassElement asyncType = getAsyncType(matchContext, returnType);
            if (isContainer(asyncType, Iterable.class)) {
                return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, asyncType), UpdateReturningManyAsyncInterceptor.class);
            }
            return typeAndInterceptorEntry(matchContext, asyncType, UpdateReturningOneAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            ClassElement reactiveType = returnType.getFirstTypeArgument().orElse(voidType(matchContext));
            if (isReactiveSingleResult(returnType)) {
                return typeAndInterceptorEntry(matchContext, reactiveType, io.micronaut.data.intercept.reactive.UpdateReturningOneReactiveInterceptor.class);
            }
            return typeAndInterceptorEntry(matchContext, reactiveType, io.micronaut.data.intercept.reactive.UpdateReturningManyReactiveInterceptor.class);
        }
        if (isContainer(returnType, Iterable.class)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), UpdateReturningManyInterceptor.class);
        } else {
            return typeAndInterceptorEntry(matchContext, returnType.getType(), UpdateReturningOneInterceptor.class);
        }
    }

    private static InterceptorMatch pickDeleteReturningInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            ClassElement asyncType = getAsyncType(matchContext, returnType);
            if (isContainer(asyncType, Iterable.class)) {
                return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, asyncType), DeleteReturningManyAsyncInterceptor.class);
            }
            return typeAndInterceptorEntry(matchContext, asyncType, DeleteReturningOneAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            ClassElement reactiveType = returnType.getFirstTypeArgument().orElse(voidType(matchContext));
            if (isReactiveSingleResult(returnType)) {
                return typeAndInterceptorEntry(matchContext, reactiveType, io.micronaut.data.intercept.reactive.DeleteReturningOneReactiveInterceptor.class);
            }
            return typeAndInterceptorEntry(matchContext, reactiveType, io.micronaut.data.intercept.reactive.DeleteReturningManyReactiveInterceptor.class);
        }
        if (isContainer(returnType, Iterable.class)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), DeleteReturningManyInterceptor.class);
        } else {
            return typeAndInterceptorEntry(matchContext, returnType.getType(), DeleteReturningOneInterceptor.class);
        }
    }

    private static InterceptorMatch pickInsertReturningInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            ClassElement asyncType = getAsyncType(matchContext, returnType);
            if (isContainer(asyncType, Iterable.class)) {
                return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, asyncType), InsertReturningManyAsyncInterceptor.class);
            }
            return typeAndInterceptorEntry(matchContext, asyncType, InsertReturningOneAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            ClassElement reactiveType = returnType.getFirstTypeArgument().orElse(voidType(matchContext));
            if (isReactiveSingleResult(returnType)) {
                return typeAndInterceptorEntry(matchContext, reactiveType, io.micronaut.data.intercept.reactive.InsertReturningOneReactiveInterceptor.class);
            }
            return typeAndInterceptorEntry(matchContext, reactiveType, io.micronaut.data.intercept.reactive.InsertReturningManyReactiveInterceptor.class);
        }
        if (isContainer(returnType, Iterable.class)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), InsertReturningManyInterceptor.class);
        } else {
            return typeAndInterceptorEntry(matchContext, returnType.getType(), InsertReturningOneInterceptor.class);
        }
    }

    static FindersUtils.InterceptorMatch pickSaveOneInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), SaveOneAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), SaveOneReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), SaveOneInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickInsertOneInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), InsertOneAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), InsertOneReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), InsertOneInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickUpdateAllEntitiesInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), UpdateAllEntriesAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), UpdateAllEntitiesReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), UpdateAllEntitiesInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickProcedureInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            ClassElement asyncType = getAsyncType(matchContext, returnType);
            if (isContainer(asyncType, Iterable.class)) {
                return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, asyncType), ProcedureReturningManyAsyncInterceptor.class);
            }
            return typeAndInterceptorEntry(matchContext, asyncType, ProcedureReturningOneAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), ProcedureReactiveInterceptor.class);
        }
        if (isContainer(returnType, Iterable.class)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), ProcedureReturningManyInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), ProcedureReturningOneInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickDeleteInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), DeleteOneAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), DeleteOneReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), DeleteOneInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickDeleteAllInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), DeleteAllAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), DeleteAllReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), DeleteAllInterceptor.class);
    }

    private static ClassElement voidType(MethodMatchContext matchContext) {
        return matchContext.getVisitorContext().getClassElement(Void.class).orElseThrow();
    }

    static FindersUtils.InterceptorMatch pickDeleteAllReturningInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        return typeAndInterceptorEntry(matchContext, returnType.getType(), DeleteAllReturningInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickSaveEntityInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), SaveEntityAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), SaveEntityReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), SaveEntityInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickInsertEntityInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), InsertEntityAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), InsertEntityReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), InsertEntityInterceptor.class);
    }

    private static ClassElement getReactiveTypeOrVoid(MethodMatchContext matchContext, ClassElement returnType) {
        return returnType.getFirstTypeArgument().orElse(voidType(matchContext));
    }

    static FindersUtils.InterceptorMatch pickSaveAllEntitiesInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), SaveAllAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), SaveAllReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), SaveAllInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickInsertAllEntitiesInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), InsertAllAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), InsertAllReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), InsertAllInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickUpdateInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), UpdateAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), UpdateReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), UpdateInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickUpdateEntityInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), UpdateEntityAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, returnType.getFirstTypeArgument().orElse(voidType(matchContext)), UpdateEntityReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), UpdateEntityInterceptor.class);
    }

    static FindersUtils.InterceptorMatch resolveFindInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        FindersUtils.InterceptorMatch entry;
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            entry = resolveAsyncFindInterceptor(matchContext, getAsyncType(matchContext, returnType));
        } else if (isReactiveType(returnType)) {
            entry = resolveReactiveFindInterceptor(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), isReactiveSingleResult(returnType));
        } else {
            entry = resolveSyncFindInterceptor(matchContext, returnType);
        }
//        if (!isValidResultType(entry.getKey())) {
//            matchContext.failAndThrow("Unsupported return type: " + entry.getKey());
//        }
        return entry;
    }

    private static FindersUtils.InterceptorMatch resolveSyncFindInterceptor(MethodMatchContext matchContext,
                                                                     ClassElement returnType) {
        if (SearchResults.class.getName().equals(returnType.getName())) {
            return new FindersUtils.InterceptorMatch(returnType, getInterceptorElement(matchContext, FindOneInterceptor.class), false);
        }
        FindInterceptorDef findInterceptorDef = matchContext.getFindInterceptors().get(returnType);
        if (findInterceptorDef != null) {
            if (findInterceptorDef.isContainer() && isContainer(returnType, findInterceptorDef.returnType())) {
                return new FindersUtils.InterceptorMatch(getFirstTypeArgumentOrFail(matchContext, returnType), findInterceptorDef.interceptor());
            } else {
                return new FindersUtils.InterceptorMatch(findInterceptorDef.returnType(), findInterceptorDef.interceptor(), false);
            }
        }
        if (isCursoredPage(matchContext, returnType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), FindCursoredPageInterceptor.class);
        } else if (isPage(matchContext, returnType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), FindPageInterceptor.class);
        } else if (isSlice(matchContext, returnType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), FindSliceInterceptor.class);
        } else if (isContainer(returnType, Iterable.class)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), FindAllInterceptor.class);
        } else if (returnType.isArray()) {
            return typeAndInterceptorEntry(matchContext, returnType.fromArray(), FindAllInterceptor.class);
        } else {
            return typeAndInterceptorEntry(matchContext, returnType, FindOneInterceptor.class);
        }
    }

    private static ClassElement getFirstTypeArgumentOrFail(MethodMatchContext matchContext, ClassElement returnType) {
        return getFirstTypeArgumentOrFail(matchContext.getMethodElement(), returnType);
    }

    private static ClassElement getFirstTypeArgumentOrFail(MethodElement methodElement, ClassElement returnType) {
        return returnType.getFirstTypeArgument().orElseThrow(failOnMissingGeneric(methodElement, returnType));
    }

    private static FindersUtils.InterceptorMatch resolveReactiveFindInterceptor(MethodMatchContext matchContext,
                                                                                ClassElement returnType,
                                                                                boolean singleResult) {
        if (SearchResults.class.getName().equals(returnType.getName())) {
            return new FindersUtils.InterceptorMatch(
                returnType,
                getInterceptorElement(matchContext, FindOneReactiveInterceptor.class),
                false
            );
        }
        if (isCursoredPage(matchContext, returnType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), FindCursoredReactivePageInterceptor.class);
        } else if (isPage(matchContext, returnType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), FindPageReactiveInterceptor.class);
        } else if (isSlice(matchContext, returnType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), FindSliceReactiveInterceptor.class);
        } else if (singleResult) {
            return typeAndInterceptorEntry(matchContext, returnType, FindOneReactiveInterceptor.class);
        } else {
            return typeAndInterceptorEntry(matchContext, returnType, FindAllReactiveInterceptor.class);
        }
    }

    private static FindersUtils.InterceptorMatch resolveAsyncFindInterceptor(MethodMatchContext matchContext, ClassElement asyncType) {
        if (SearchResults.class.getName().equals(asyncType.getName())) {
            return new FindersUtils.InterceptorMatch(
                asyncType,
                getInterceptorElement(matchContext, FindOneAsyncInterceptor.class),
                false
            );
        }
        if (isCursoredPage(matchContext, asyncType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, asyncType), FindCursoredAsyncPageInterceptor.class);
        } else if (isPage(matchContext, asyncType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, asyncType), FindPageAsyncInterceptor.class);
        } else if (isSlice(matchContext, asyncType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, asyncType), FindSliceAsyncInterceptor.class);
        } else if (isContainer(asyncType, Iterable.class)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, asyncType), FindAllAsyncInterceptor.class);
        } else if (isContainer(asyncType, Optional.class)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, asyncType), FindOneAsyncInterceptor.class);
        } else {
            return typeAndInterceptorEntry(matchContext, asyncType, FindOneAsyncInterceptor.class);
        }
    }

    static FindersUtils.InterceptorMatch pickCountInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), CountAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), CountReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), CountInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickExistsInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), ExistsByAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), ExistsByReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), ExistsByInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickFindByIdInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), FindByIdAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), FindByIdReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), FindByIdInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickFindOneInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(matchContext, getAsyncType(matchContext, returnType), FindOneAsyncInterceptor.class);
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), FindOneReactiveInterceptor.class);
        }
        return typeAndInterceptorEntry(matchContext, returnType.getType(), FindOneInterceptor.class);
    }

    static FindersUtils.InterceptorMatch pickCountSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.CountAsyncSpecificationInterceptor")
            );
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.CountReactiveSpecificationInterceptor")
            );
        }
        return typeAndInterceptorEntry(returnType.getType(),
            getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.CountSpecificationInterceptor")
        );
    }

    static FindersUtils.InterceptorMatch pickDeleteAllSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.DeleteAllAsyncSpecificationInterceptor")
            );
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(voidType(matchContext)),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.DeleteAllReactiveSpecificationInterceptor")
            );
        }
        return typeAndInterceptorEntry(returnType.getType(),
            getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.DeleteAllSpecificationInterceptor")
        );
    }

    static FindersUtils.InterceptorMatch pickSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return pickFindAsyncSpecInterceptor(matchContext, getAsyncType(matchContext, returnType));
        } else if (isReactiveType(returnType)) {
            return pickFindReactiveSpecInterceptor(matchContext, getFirstTypeArgumentOrFail(matchContext, returnType), isReactiveSingleResult(returnType));
        }
        return pickFindSyncSpecInterceptor(matchContext, returnType);
    }

    private static FindersUtils.InterceptorMatch pickFindSyncSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isCursoredPage(matchContext, returnType) || isPage(matchContext, returnType)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.FindPageSpecificationInterceptor")
            );
        } else if (isContainer(returnType, Iterable.class) || isContainer(returnType, Stream.class)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.FindAllSpecificationInterceptor")
            );
         } else if (isContainer(returnType, Optional.class)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.FindOneSpecificationInterceptor")
            );
        } else {
            return typeAndInterceptorEntry(returnType.getType(),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.FindOneSpecificationInterceptor")
            );
        }
    }

    private static FindersUtils.InterceptorMatch pickFindAsyncSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isCursoredPage(matchContext, returnType) || isPage(matchContext, returnType)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.FindPageAsyncSpecificationInterceptor")
            );
        } else if (isContainer(returnType, Iterable.class)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.FindAllAsyncSpecificationInterceptor")
            );
        } else if (isContainer(returnType, Optional.class)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.FindOneAsyncSpecificationInterceptor")
            );
        } else {
            return typeAndInterceptorEntry(returnType,
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.FindOneAsyncSpecificationInterceptor")
            );
        }
    }

    private static FindersUtils.InterceptorMatch pickFindReactiveSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType, boolean singleResult) {
        if (isCursoredPage(matchContext, returnType) || isPage(matchContext, returnType)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.FindPageReactiveSpecificationInterceptor")
            );
        } else if (isContainer(returnType, Iterable.class)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.FindAllReactiveSpecificationInterceptor")
            );
        } else if (isContainer(returnType, Optional.class)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.FindOneReactiveSpecificationInterceptor")
            );
        } else if (singleResult) {
            return typeAndInterceptorEntry(returnType,
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.FindOneReactiveSpecificationInterceptor")
            );
        } else {
            return typeAndInterceptorEntry(returnType,
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.FindAllReactiveSpecificationInterceptor")
            );
        }
    }

    private static Supplier<ProcessingException> failOnMissingGeneric(MethodElement methodElement, ClassElement returnType) {
        return () -> new ProcessingException(methodElement, "Expected a type " + returnType.getName() + " to have a generic value, got: " + returnType);
    }

    static FindersUtils.InterceptorMatch pickUpdateAllSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.UpdateAllAsyncSpecificationInterceptor")
            );
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(returnType.getFirstTypeArgument().orElse(voidType(matchContext)),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.UpdateAllReactiveSpecificationInterceptor")
            );
        }
        return typeAndInterceptorEntry(returnType.getType(),
            getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.UpdateAllSpecificationInterceptor")
        );
    }

    static FindersUtils.InterceptorMatch pickExistsSpecInterceptor(MethodMatchContext matchContext, ClassElement returnType) {
        if (isFutureType(matchContext.getMethodElement(), returnType)) {
            return typeAndInterceptorEntry(getAsyncType(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.async.ExistsAsyncSpecificationInterceptor")
            );
        } else if (isReactiveType(returnType)) {
            return typeAndInterceptorEntry(getFirstTypeArgumentOrFail(matchContext, returnType),
                getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.reactive.ExistsReactiveSpecificationInterceptor")
            );
        }
        return typeAndInterceptorEntry(returnType.getType(),
            getInterceptorElement(matchContext, "io.micronaut.data.runtime.intercept.criteria.ExistsSpecificationInterceptor")
        );
    }

    static ClassElement getAsyncType(MethodMatchContext matchContext,
                                     ClassElement returnType) {
        MethodElement methodElement = matchContext.getMethodElement();
        if (methodElement.isSuspend()) {
            ClassElement coroutineProducedType = TypeUtils.getKotlinCoroutineProducedType(methodElement);
            return coroutineProducedType == null ? voidType(matchContext) : coroutineProducedType;
        }
        return getFirstTypeArgumentOrFail(methodElement, returnType);
    }

    static FindersUtils.InterceptorMatch typeAndInterceptorEntry(MethodMatchContext matchContext,
                                                                 ClassElement type,
                                                                 Class<? extends DataInterceptor> interceptor) {
        return new FindersUtils.InterceptorMatch(type, getInterceptorElement(matchContext, interceptor));
    }

    static FindersUtils.InterceptorMatch typeAndInterceptorEntry(ClassElement type, ClassElement interceptor) {
        return new FindersUtils.InterceptorMatch(type, interceptor);
    }

    static boolean isFutureType(MethodElement methodElement, @Nullable ClassElement type) {
        return methodElement.isSuspend() || isOneOfContainers(type, CompletionStage.class, Future.class);
    }

    static boolean isReactiveType(@Nullable ClassElement type) {
        return isContainer(type, Publisher.class)
            || TypeUtils.isReactiveType(type)
            && type != null && (type.getTypeArguments().isEmpty() || isContainer(type, type.getName())); // Validate container argument
    }

    static boolean isCursoredPage(MethodMatchContext methodMatchContext, ClassElement typeArgument) {
        boolean matches = methodMatchContext.isTypeInRole(typeArgument, TypeRole.CURSORED_PAGE);
        if (matches && !methodMatchContext.hasParameterInRole(TypeRole.PAGEABLE)) {
            methodMatchContext.fail("Method must accept an argument that is a Pageable");
        }
        return matches;
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

    static boolean isContainer(@Nullable ClassElement typeArgument, Class<?> containerType) {
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

    static boolean isContainer(@Nullable ClassElement typeArgument, ClassElement containerType) {
        if (typeArgument == null) {
            return false;
        }
        if (typeArgument.equals(containerType)) {
            ClassElement type = typeArgument.getFirstTypeArgument().orElse(null);
            if (type == null) {
                throw new MatchFailedException("'" + containerType + "' return type missing type argument");
            }
            return true;
        }
        return false;
    }

    static boolean isOneOfContainers(@Nullable ClassElement typeArgument, Class<?>... containers) {
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
            || isContainer(returnType, "io.reactivex.rxjava3.core.Single")
            || isContainer(returnType, "reactor.core.publisher.Mono");
    }

    /**
     * Obtain the interceptor element for the given class.
     *
     * @param matchContext The match context
     * @param type         The type
     * @return The element
     */
    static ClassElement getInterceptorElement(MethodMatchContext matchContext, Class<? extends DataInterceptor> type) {
        return matchContext.getVisitorContext().getClassElement(type).orElseGet(() -> new FindersUtils.DynamicClassElement(type));
    }

    /**
     * Obtain the interceptor element for the given class name.
     *
     * @param matchContext The match context
     * @param type         The type
     * @return The element
     */
    static ClassElement getInterceptorElement(MethodMatchContext matchContext, String type) {
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

        @Override
        public Object getNativeType() {
            return type;
        }
    }

    /**
     * The interceptor match.
     * @param returnType The return type
     * @param interceptor The interceptor
     * @param validateReturnType True if the return type needs to be validated
     */
    record InterceptorMatch(ClassElement returnType, ClassElement interceptor, boolean validateReturnType) {

        public InterceptorMatch(ClassElement returnType, ClassElement interceptor) {
            this(returnType, interceptor, true);
        }
    }
}
