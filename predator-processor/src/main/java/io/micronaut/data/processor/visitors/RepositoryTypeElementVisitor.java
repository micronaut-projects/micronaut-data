package io.micronaut.data.processor.visitors;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.encoder.EncodedQuery;
import io.micronaut.data.model.query.encoder.QueryEncoder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.visitors.finders.*;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nullable;
import java.util.*;

@Internal
public class RepositoryTypeElementVisitor implements TypeElementVisitor<Repository, Object> {

    private static final String[] DEFAULT_PAGINATORS = {Pageable.class.getName()};
    private ClassElement currentClass;
    private QueryEncoder queryEncoder;
    private String[] paginationTypes = DEFAULT_PAGINATORS;
    private List<PredatorMethodCandidate> finders = Arrays.asList(
            new FindByFinder(),
            new ExistsByFinder(),
            new SaveMethod(),
            new SaveAllMethod(),
            new ListMethod()
    );

    public RepositoryTypeElementVisitor() {
        OrderUtil.sort(finders);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        this.currentClass = element;
        queryEncoder = resolveQueryEncoder(element, context);
        paginationTypes = resolvePaginatorTypes(element);
        if (queryEncoder == null) {
            context.fail("QueryEncoder not present on annotation processor path", element);
        }
    }

    private String[] resolvePaginatorTypes(ClassElement element) {
        return element.getValue(Repository.class, "paginationTypes", AnnotationClassValue[].class)
                .map(annotationClassValues ->
                        {
                            String[] names = Arrays.stream(annotationClassValues)
                                    .map(AnnotationClassValue::getName).toArray(String[]::new);
                            if (ArrayUtils.isNotEmpty(names)) {
                                return names;
                            }
                            return DEFAULT_PAGINATORS;
                        }
                ).orElse(DEFAULT_PAGINATORS);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (queryEncoder != null && currentClass != null && element.isAbstract() && !element.isStatic()) {
            for (PredatorMethodCandidate finder : finders) {
                if (finder.isMethodMatch(element)) {
                    PersistentEntity entity = resolvePersistentEntity(element);
                    String idType = resolveIdType(entity);
                    ParameterElement[] parameters = element.getParameters();
                    ParameterElement pageParam = Arrays.stream(parameters).filter(p -> {
                        ClassElement t = p.getType();
                        return t != null && Arrays.stream(paginationTypes).anyMatch(t::isAssignable);
                    }).findFirst().orElse(null);


                    if (entity == null) {
                        context.fail("Unable to establish persistent entity to query", element);
                        return;
                    }

                    PredatorMethodInfo methodInfo = finder.buildMatchInfo(new MethodMatchContext(
                            entity,
                            context,
                            element,
                            pageParam,
                            parameters
                    ));
                    if (methodInfo != null) {

                        Query queryObject = methodInfo.getQuery();
                        Map<String, String> parameterBinding = null;
                        if (queryObject != null) {
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

                        Class<? extends PredatorInterceptor> runtimeInterceptor = methodInfo.getRuntimeInterceptor();

                        if (runtimeInterceptor != null) {
                            Map<String, String> finalParameterBinding = parameterBinding;
                            element.annotate(PredatorMethod.class, annotationBuilder -> {
                                annotationBuilder.member("rootEntity", new AnnotationClassValue<>(entity.getName()));
                                if (idType != null) {
                                    annotationBuilder.member("idType", idType);
                                }
                                annotationBuilder.member("interceptor", runtimeInterceptor);
                                if (finalParameterBinding != null) {
                                    AnnotationValue<?>[] annotationParameters = new AnnotationValue[finalParameterBinding.size()];
                                    int i = 0;
                                    for (Map.Entry<String, String> entry : finalParameterBinding.entrySet()) {
                                        annotationParameters[i++] = AnnotationValue.builder(Property.class)
                                                .member("name", entry.getKey())
                                                .member("value", entry.getValue())
                                                .build();
                                    }
                                    annotationBuilder.member("parameterBinding", annotationParameters);
                                }
                                Optional<ParameterElement> entityParam = Arrays.stream(parameters).filter(p -> {
                                    ClassElement t = p.getGenericType();
                                    return t != null && t.isAssignable(entity.getName());
                                }).findFirst();
                                entityParam.ifPresent(parameterElement -> annotationBuilder.member("entity", parameterElement.getName()));

                                if (pageParam != null) {
                                    annotationBuilder.member("pageable", pageParam.getName());
                                }
                            });
                            return;
                        } else {
                            context.fail("Unable to implement Repository method: " + currentClass.getSimpleName() + "." + element.getName() + "(..). No possible runtime implementations found.", element);
                        }
                    }

                }
            }

            context.fail("Unable to implement Repository method: " + currentClass.getSimpleName() + "." + element.getName() + "(..). No possible implementations found.", element);
        }
    }

    private @Nullable String resolveIdType(PersistentEntity entity) {
        Map<String, ClassElement> typeArguments = currentClass.getTypeArguments(io.micronaut.data.repository.Repository.class);
        if (!typeArguments.isEmpty()) {
            ClassElement ce = typeArguments.get("ID");
            if (ce != null) {
                return ce.getName();
            }
        }
        PersistentProperty identity = entity.getIdentity();
        if (identity != null) {
            return identity.getName();
        }
        return null;
    }

    private @Nullable PersistentEntity resolvePersistentEntity(MethodElement element) {
        ClassElement returnType = element.getGenericReturnType();
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

    private QueryEncoder resolveQueryEncoder(Element element, VisitorContext context) {
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
