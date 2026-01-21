/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.jakarta.tck.runtime;

import ee.jakarta.tck.data.framework.junit.anno.Assertion;
import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.data.annotation.Projection;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@NullUnmarked
@Internal
public final class TestClassVisitor implements TypeElementVisitor<Object, Object> {

    private final boolean isJdbcImplementation;
    private final boolean isMogngoDBImplementation;

    private static final Set<String> INTROSPECTED = Set.of(
        "ee.jakarta.tck.data.framework.read.only.CardinalNumber",
        "ee.jakarta.tck.data.framework.read.only.HexInfo",
        "ee.jakarta.tck.data.framework.read.only.NumberInfo",
        "ee.jakarta.tck.data.framework.read.only.WholeNumber"
    );

    public TestClassVisitor() {
        Properties prop = new Properties();
        try {
            //load a properties file from class path, inside static method
            InputStream resourceAsStream = getClass().getResourceAsStream("/aprocessor.properties");
            if (resourceAsStream != null) {
                prop.load(resourceAsStream);
            }
        } catch (IOException ex) {
            // ignore
        }
        Object implementation = prop.getOrDefault("implementation", "");
        isJdbcImplementation = implementation.equals("jdbc");
        isMogngoDBImplementation = implementation.equals("mongodb");
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public int getOrder() {
        return 88;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasStereotype(Repository.class) && isJdbcImplementation) {
            element.annotate(JdbcRepository.class, annotationValueBuilder -> {
                annotationValueBuilder.member("dialect", Dialect.H2);
            });
        }
        if (element.hasStereotype(Repository.class) && isMogngoDBImplementation) {
            element.annotate(MongoRepository.class);
        }
        if (element.getName().startsWith("ee.jakarta.tck.data") && !element.isEnum()) {
            if (element.hasStereotype(Introspected.class)) {
                element.annotate(Introspected.class, builder -> {
                    builder.member("accessKind", new Introspected.AccessKind[]{Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD});
                    builder.member("visibility", Introspected.Visibility.ANY);
                });
            } else if (INTROSPECTED.contains(element.getName())) {
                element.annotate(Introspected.class);
            }
            element.annotate(Executable.class);
            element.annotate(Prototype.class);

            if (element.isRecord()) {
                // Remove after https://github.com/micronaut-projects/micronaut-core/pull/12184
                JavaNativeElement nativeType = (JavaNativeElement) element.getNativeType();
                Element e = nativeType.element();
                List<? extends Element> enclosedElements = e.getEnclosedElements();
                for (Element el : enclosedElements) {
                    if (el instanceof RecordComponentElement recordComponentElement) {
                        for (AnnotationMirror annotationMirror : recordComponentElement.getAnnotationMirrors()) {
                            if ("jakarta.data.repository.Select".equals(annotationMirror.getAnnotationType().toString())) {
                                AnnotationValue value = annotationMirror.getElementValues().entrySet().iterator().next().getValue();
                                String val = value.getValue().toString();
                                for (PropertyElement beanProperty : element.getBeanProperties()) {
                                    if (recordComponentElement.toString().equals(beanProperty.getName())) {
                                        beanProperty.annotate(Projection.class, builder -> builder.value(val));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            element.getMethods().forEach(ce -> {
                if (ce.isStatic() || !ce.isAccessible()) {
                    ce.annotate(Vetoed.class);
                } else {
                    ce.annotate(Executable.class);
                }
            });
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        element.removeAnnotation(Assertion.class);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (isJdbcImplementation || isMogngoDBImplementation) {
            if (element.getOwningType().hasStereotype(Entity.class)) {
                element.annotate(Nullable.class);
            }
            if (element.hasStereotype(ElementCollection.class)) {
                element.annotate(Transient.class);
            }
        }
        if (isMogngoDBImplementation && element.getType().getName().equals("char")) {
            MongoUtils.bson(element);
        }
    }

}
