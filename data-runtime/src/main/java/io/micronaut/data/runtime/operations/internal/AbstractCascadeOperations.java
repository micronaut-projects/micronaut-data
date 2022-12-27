/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.operations.internal;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentAssociationPath;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

/**
 * Abstract cascade operations.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
abstract class AbstractCascadeOperations {

    private final ConversionService conversionService;

    /**
     * Default constructor.
     *
     * @param conversionService The conversion service.
     */
    protected AbstractCascadeOperations(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * Cascade on the entity instance and collect cascade operations.
     *
     * @param annotationMetadata The annotationMetadata
     * @param repositoryType     The repositoryType
     * @param fkOnly             Is FK only
     * @param cascadeType        The cascadeType
     * @param ctx                The cascade context
     * @param persistentEntity   The persistent entity
     * @param entity             The entity instance
     * @param cascadeOps         The cascade operations
     * @param <T>                The entity type
     */
    protected <T> void cascade(AnnotationMetadata annotationMetadata, Class<?> repositoryType,
                               boolean fkOnly,
                               Relation.Cascade cascadeType,
                               CascadeContext ctx,
                               RuntimePersistentEntity<T> persistentEntity,
                               T entity,
                               List<CascadeOp> cascadeOps) {
        for (RuntimeAssociation<T> association : persistentEntity.getAssociations()) {
            BeanProperty<T, Object> beanProperty = (BeanProperty<T, Object>) association.getProperty();
            Object child = beanProperty.get(entity);
            if (child == null) {
                continue;
            }
            if (association instanceof Embedded) {
                cascade(annotationMetadata, repositoryType, fkOnly, cascadeType, ctx.embedded(association),
                        (RuntimePersistentEntity) association.getAssociatedEntity(),
                        child,
                        cascadeOps);
                continue;
            }
            if (association.doesCascade(cascadeType) && (fkOnly || !association.isForeignKey())) {
                if (association.getInverseSide().map(assoc -> ctx.rootAssociations.contains(assoc) || ctx.associations.contains(assoc)).orElse(false)) {
                    continue;
                }
                final RuntimePersistentEntity<Object> associatedEntity = (RuntimePersistentEntity<Object>) association.getAssociatedEntity();
                switch (association.getKind()) {
                    case ONE_TO_ONE:
                    case MANY_TO_ONE:
                        cascadeOps.add(new CascadeOneOp(annotationMetadata, repositoryType, ctx.relation(association), cascadeType, associatedEntity, child));
                        continue;
                    case ONE_TO_MANY:
                    case MANY_TO_MANY:
                        final PersistentAssociationPath inverse = association.getInversePathSide().orElse(null);
                        Iterable<Object> children = (Iterable<Object>) association.getProperty().get(entity);
                        if (children == null || !children.iterator().hasNext()) {
                            continue;
                        }
                        if (inverse != null && inverse.getAssociation().getKind() == Relation.Kind.MANY_TO_ONE) {
                            List<Object> entities = new ArrayList<>(CollectionUtils.iterableToList(children));
                            for (ListIterator<Object> iterator = entities.listIterator(); iterator.hasNext(); ) {
                                Object c = iterator.next();
                                Object newC = inverse.setPropertyValue(c, entity);
                                if (c != newC) {
                                    iterator.set(newC);
                                }
                            }
                            children = entities;
                        }
                        cascadeOps.add(new CascadeManyOp(annotationMetadata, repositoryType, ctx.relation(association), cascadeType, associatedEntity, children));
                        continue;
                    default:
                        throw new IllegalArgumentException("Cannot cascade for relation: " + association.getKind());
                }
            }
        }
    }

    /**
     * Process after a child element has been cascaded.
     *
     * @param entity       The parent entity.
     * @param associations The association leading to the child
     * @param prevChild    The previous child value
     * @param newChild     The new child value
     * @param <T>          The entity type
     * @return The entity instance
     */
    protected <T> T afterCascadedOne(T entity, List<Association> associations, Object prevChild, Object newChild) {
        RuntimeAssociation<Object> association = (RuntimeAssociation<Object>) associations.iterator().next();
        if (associations.size() == 1) {
            if (association.isForeignKey()) {
                PersistentAssociationPath inverse = association.getInversePathSide().orElse(null);
                if (inverse != null) {
                    //don't cast to BeanProperty<T..> here because its the inverse, so we want to set the entity onto the newChild
                    newChild = inverse.setPropertyValue(newChild, entity);
                }
            }
            if (prevChild != newChild) {
                entity = setProperty((BeanProperty<T, Object>) association.getProperty(), entity, newChild);
            }
            return entity;
        } else {
            BeanProperty<T, Object> property = (BeanProperty<T, Object>) association.getProperty();
            Object innerEntity = property.get(entity);
            Object newInnerEntity = afterCascadedOne(innerEntity, associations.subList(1, associations.size()), prevChild, newChild);
            if (newInnerEntity != innerEntity) {
                innerEntity = convertAndSetWithValue(property, entity, newInnerEntity);
            }
            return (T) innerEntity;
        }
    }

    /**
     * Process after a children element has been cascaded.
     *
     * @param entity       The parent entity.
     * @param associations The association leading to the child
     * @param prevChildren The previous children value
     * @param newChildren  The new children value
     * @param <T>          The entity type
     * @return The entity instance
     */
    protected <T> T afterCascadedMany(T entity, List<Association> associations, Iterable<Object> prevChildren, List<Object> newChildren) {
        RuntimeAssociation<Object> association = (RuntimeAssociation<Object>) associations.iterator().next();
        if (associations.size() == 1) {
            for (ListIterator<Object> iterator = newChildren.listIterator(); iterator.hasNext(); ) {
                Object c = iterator.next();
                if (association.isForeignKey()) {
                    PersistentAssociationPath inverse = association.getInversePathSide().orElse(null);
                    if (inverse != null) {
                        Object newc = inverse.setPropertyValue(c, entity);
                        if (c != newc) {
                            iterator.set(newc);
                        }
                    }
                }
            }
            if (prevChildren != newChildren) {
                entity = convertAndSetWithValue((BeanProperty<T, Object>) association.getProperty(), entity, newChildren);
            }
            return entity;
        } else {
            BeanProperty<T, Object> property = (BeanProperty<T, Object>) association.getProperty();
            Object innerEntity = property.get(entity);
            Object newInnerEntity = afterCascadedMany(innerEntity, associations.subList(1, associations.size()), prevChildren, newChildren);
            if (newInnerEntity != innerEntity) {
                innerEntity = convertAndSetWithValue(property, entity, newInnerEntity);
            }
            return (T) innerEntity;
        }
    }

    private <X, Y> X setProperty(BeanProperty<X, Y> beanProperty, X x, Y y) {
        if (beanProperty.isReadOnly()) {
            return beanProperty.withValue(x, y);
        }
        beanProperty.set(x, y);
        return x;
    }

    private <B, T> B convertAndSetWithValue(BeanProperty<B, T> beanProperty, B bean, T value) {
        Argument<T> argument = beanProperty.asArgument();
        final ArgumentConversionContext<T> context = ConversionContext.of(argument);
        T convertedValue = conversionService.convert(value, context).orElseThrow(() ->
                new ConversionErrorException(argument, context.getLastError()
                        .orElse(() -> new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + beanProperty.getType())))
        );
        if (beanProperty.isReadOnly()) {
            return beanProperty.withValue(bean, convertedValue);
        }
        beanProperty.set(bean, convertedValue);
        return bean;
    }

    private static List<Association> associated(List<Association> associations, Association association) {
        if (associations == null) {
            return Collections.singletonList(association);
        }
        List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
        newAssociations.addAll(associations);
        newAssociations.add(association);
        return newAssociations;
    }

    /**
     * The base cascade operation.
     */
    @SuppressWarnings("VisibilityModifier")
    protected abstract static class CascadeOp {

        public final AnnotationMetadata annotationMetadata;
        public final Class<?> repositoryType;
        public final CascadeContext ctx;
        public final Relation.Cascade cascadeType;
        public final RuntimePersistentEntity<Object> childPersistentEntity;

        CascadeOp(AnnotationMetadata annotationMetadata,
                  Class<?> repositoryType,
                  CascadeContext ctx,
                  Relation.Cascade cascadeType,
                  RuntimePersistentEntity<Object> childPersistentEntity) {
            this.annotationMetadata = annotationMetadata;
            this.repositoryType = repositoryType;
            this.ctx = ctx;
            this.cascadeType = cascadeType;
            this.childPersistentEntity = childPersistentEntity;
        }
    }

    /**
     * The cascade operation of one entity.
     */
    @SuppressWarnings("VisibilityModifier")
    protected static final class CascadeOneOp extends CascadeOp {

        public final Object child;

        CascadeOneOp(AnnotationMetadata annotationMetadata, Class<?> repositoryType,
                     CascadeContext ctx, Relation.Cascade cascadeType, RuntimePersistentEntity<Object> childPersistentEntity, Object child) {
            super(annotationMetadata, repositoryType, ctx, cascadeType, childPersistentEntity);
            this.child = child;
        }
    }

    /**
     * The cascade operation of multiple entities - @Many mappings.
     */
    @SuppressWarnings("VisibilityModifier")
    protected static final class CascadeManyOp extends CascadeOp {

        public final Iterable<Object> children;

        CascadeManyOp(AnnotationMetadata annotationMetadata, Class<?> repositoryType,
                      CascadeContext ctx, Relation.Cascade cascadeType, RuntimePersistentEntity<Object> childPersistentEntity,
                      Iterable<Object> children) {
            super(annotationMetadata, repositoryType, ctx, cascadeType, childPersistentEntity);
            this.children = children;
        }
    }

    /**
     * The cascade context.
     */
    @SuppressWarnings("VisibilityModifier")
    protected static final class CascadeContext {

        /**
         * The associations leading to the parent.
         */
        public final List<Association> rootAssociations;
        /**
         * The parent instance that is being cascaded.
         */
        public final Object parent;
        /**
         * The parent instance that is being cascaded.
         */
        public final RuntimePersistentEntity<Object> parentPersistentEntity;
        /**
         * The associations leading to the cascaded instance.
         */
        public final List<Association> associations;

        /**
         * Create a new instance.
         *
         * @param rootAssociations       The root associations.
         * @param parent                 The parent
         * @param parentPersistentEntity The parentPersistentEntity
         * @param associations           The associations
         */
        CascadeContext(List<Association> rootAssociations, Object parent, RuntimePersistentEntity<Object> parentPersistentEntity, List<Association> associations) {
            this.rootAssociations = rootAssociations;
            this.parent = parent;
            this.parentPersistentEntity = parentPersistentEntity;
            this.associations = associations;
        }

        public static CascadeContext of(List<Association> rootAssociations, Object parent, RuntimePersistentEntity<Object> parentPersistentEntity) {
            return new CascadeContext(rootAssociations, parent, parentPersistentEntity, Collections.emptyList());
        }

        /**
         * Cascade embedded association.
         *
         * @param association The embedded association
         * @return The context
         */
        CascadeContext embedded(Association association) {
            return new CascadeContext(rootAssociations, parent, parentPersistentEntity, associated(associations, association));
        }

        /**
         * Cascade relation association.
         *
         * @param association The relation association
         * @return The context
         */
        CascadeContext relation(Association association) {
            return new CascadeContext(rootAssociations, parent, parentPersistentEntity, associated(associations, association));
        }

        /**
         * Get last association.
         *
         * @return last association
         */
        public Association getAssociation() {
            return CollectionUtils.last(associations);
        }

    }

}
