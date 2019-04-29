package io.micronaut.data.processor.visitors;

import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.finders.FindByFinder;
import io.micronaut.data.model.finders.FinderMethod;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.encoder.EncodedQuery;
import io.micronaut.data.model.query.encoder.QueryEncoder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class RepositoryTypeElementVisitor implements TypeElementVisitor<Repository, Object> {

    private ClassElement currentClass;
    private List<FinderMethod> finders = Arrays.asList(
            new FindByFinder()
    );

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        this.currentClass = element;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (currentClass != null && element.isAbstract() && !element.isStatic()) {
            final String methodName = element.getName();
            for (FinderMethod finder : finders) {
                if (finder.isMethodMatch(methodName)) {
                    PersistentEntity entity = resolvePersistentEntity(element);

                    if (entity == null) {
                        context.fail("Unable to establish persistent entity to query", element);
                        return;
                    }

                    final Query queryObject = finder.buildQuery(entity, element, context);
                    QueryEncoder queryEncoder = resolveQueryEncoder(element, context);

                    if (queryEncoder == null) {
                        context.fail("QueryEncoder not present on annotation processor path", element);
                        return;
                    }

                    EncodedQuery encodedQuery;
                    try {
                        encodedQuery = queryEncoder.encodeQuery(queryObject);
                    } catch (Exception e) {
                        context.fail("Invalid query method: " + e.getMessage(), element);
                        return;
                    }

                    element.annotate(io.micronaut.data.annotation.Query.class, annotationBuilder ->
                            annotationBuilder.value(encodedQuery.getQuery())
                    );
                    return;
                }
            }
        }
    }

    private PersistentEntity resolvePersistentEntity(MethodElement element) {
        ClassElement returnType = element.getReturnType();
        return resolvePersistentEntity(returnType);
    }

    private PersistentEntity resolvePersistentEntity(ClassElement returnType) {
        if (returnType != null) {
            if (returnType.hasAnnotation(Persisted.class)) {
                return new SourcePersistentEntity(returnType);
            } else {
                Collection<ClassElement> typeArguments = returnType.getTypeArguments().values();
                for (ClassElement typeArgument : typeArguments) {
                    PersistentEntity entity = resolvePersistentEntity(typeArgument);
                    if (entity != null) {
                        return entity;
                    }
                }
            }
        }
        return null;
    }

    private QueryEncoder resolveQueryEncoder(MethodElement element, VisitorContext context) {
        return element.getValue(
                                Repository.class,
                                "queryEncoder",
                                String.class
                        ).flatMap(type -> {
                            Object o = InstantiationUtils.tryInstantiate(type, RepositoryTypeElementVisitor.class.getClassLoader()).orElse(null);
                            if (o instanceof QueryEncoder) {
                                return Optional.of((QueryEncoder) o);
                            } else {
                                context.fail("QueryEncoder of type [" + type + "] not present on annotation processor path", element);
                                return Optional.empty();
                            }
                        }).orElse(null);
    }
}
