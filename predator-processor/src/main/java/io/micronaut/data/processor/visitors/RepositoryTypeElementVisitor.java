package io.micronaut.data.processor.visitors;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.intercept.annotation.PredatorMethod;
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

import java.util.*;

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
            for (FinderMethod finder : finders) {
                if (finder.isMethodMatch(element)) {
                    PersistentEntity entity = resolvePersistentEntity(element);

                    if (entity == null) {
                        context.fail("Unable to establish persistent entity to query", element);
                        return;
                    }

                    final Query queryObject = finder.buildQuery(entity, element, context);
                    Map<String, String> parameterBinding = null;
                    if (queryObject != null) {
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

                        parameterBinding = encodedQuery.getParameters();
                        element.annotate(io.micronaut.data.annotation.Query.class, annotationBuilder ->
                                annotationBuilder.value(encodedQuery.getQuery())
                        );
                    }

                    Class<? extends PredatorInterceptor> runtimeInterceptor =
                            finder.getRuntimeInterceptor(entity, element, context);

                    if (runtimeInterceptor != null) {
                        Map<String, String> finalParameterBinding = parameterBinding;
                        element.annotate(PredatorMethod.class, annotationBuilder -> {
                            annotationBuilder.member("rootEntity", new AnnotationClassValue<>(entity.getName()));
                            annotationBuilder.member("interceptor", runtimeInterceptor);
                            if (finalParameterBinding != null) {
                                AnnotationValue<?>[] parameters = new AnnotationValue[finalParameterBinding.size()];
                                int i = 0;
                                for (Map.Entry<String, String> entry : finalParameterBinding.entrySet()) {
                                    parameters[i++] = AnnotationValue.builder(Property.class)
                                                .member("name", entry.getKey())
                                                .member("value", entry.getValue())
                                                .build();
                                }
                                annotationBuilder.member("parameterBinding", parameters);
                            }
                        });
                    }
                    return;
                }
            }

            context.fail("Unable to implement Repository method. No possible implementations found.", element);
        }
    }

    private PersistentEntity resolvePersistentEntity(MethodElement element) {
        ClassElement returnType = element.getReturnType();
        PersistentEntity entity = resolvePersistentEntity(returnType);
        if (entity != null) {
            return entity;
        } else {
            Map<String, ClassElement> typeArguments = currentClass.getTypeArguments(io.micronaut.data.repository.Repository.class);
            if (!typeArguments.isEmpty()) {
                ClassElement ce = typeArguments.get("E");
                if (ce != null) {
                    return new SourcePersistentEntity(ce);
                }
            }
        }
        return null;
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
