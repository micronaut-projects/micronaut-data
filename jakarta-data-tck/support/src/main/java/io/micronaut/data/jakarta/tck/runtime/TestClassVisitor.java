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
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeableGenerated;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

@NullUnmarked
@Internal
public final class TestClassVisitor implements TypeElementVisitor<Object, Object> {

    private final boolean isJdbcImplementation;
    private final boolean isMongoDBImplementation;
    private final boolean isNeo4jImplementation;

    private boolean introspected;

    private static final String COUNTRY = "ee.jakarta.tck.data.framework.read.only.Country";
    private static final String COUNTRIES = "ee.jakarta.tck.data.framework.read.only.Countries";
    private static final String COUNTRY_CAPITAL = "capital";
    // A query no test runs: FilterExtension disables the tests that call these methods
    private static final String NO_CAPITAL_QUERY = "MATCH (n:Country) WHERE false RETURN n";
    private static final Set<String> FIND_BY_CAPITAL = Set.of("withCapitalBiggerThan", "withCapitalNamed");

    private static final Set<String> INTROSPECTED = Set.of(
        "ee.jakarta.tck.data.framework.read.only.CardinalNumber",
        "ee.jakarta.tck.data.framework.read.only.HexInfo",
        "ee.jakarta.tck.data.framework.read.only.NumberInfo",
        "ee.jakarta.tck.data.framework.read.only.WholeNumber",
        "ee.jakarta.tck.data.framework.read.only.FruitSummary"
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
        isMongoDBImplementation = implementation.equals("mongodb");
        isNeo4jImplementation = implementation.equals("neo4j");
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
        if (!introspected) {
           INTROSPECTED.forEach(bean -> context.getClassElement(bean).orElseThrow().annotate(Introspected.class));
           introspected = true;
        }
        if (element.hasStereotype(Repository.class) && isJdbcImplementation) {
            element.annotate(JdbcRepository.class, annotationValueBuilder -> {
                annotationValueBuilder.member("dialect", Dialect.H2);
            });
        }
        if (element.hasStereotype(Repository.class) && isMongoDBImplementation) {
            element.annotate(MongoRepository.class);
        }
        if (isNeo4jImplementation) {
            if (element.hasStereotype(Repository.class)) {
                element.annotate(Neo4jRepository.class);
            }
            if (element.hasStereotype(Entity.class)) {
                // The graph processor maps nodes, not JPA entities: give every TCK entity a label
                element.annotate(Node.class);
            }
        }
        if (element.getName().startsWith("ee.jakarta.tck.data") && !element.isEnum()) {
            annotateTckClass(element);
        }
    }

    private void annotateTckClass(ClassElement element) {
        if (element.hasStereotype(Introspected.class)) {
            element.annotate(Introspected.class, builder -> {
                builder.member("accessKind", new Introspected.AccessKind[]{Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD});
                builder.member("visibility", Introspected.Visibility.ANY);
            });
        }
        element.annotate(Executable.class);
        element.annotate(Prototype.class);
        if (isMongoDBImplementation || isNeo4jImplementation) {
            // The field access forced above names a field such as isOdd after the field, while serde source
            // generation names it after its isOdd() getter. Keep the runtime serdes, which follow the introspection
            element.annotate(SerdeableGenerated.class, builder -> builder.member("skip", true));
        }

        element.getMethods().forEach(ce -> {
            if (ce.isStatic() || !ce.isAccessible()) {
                ce.annotate(Vetoed.class);
            } else {
                ce.annotate(Executable.class);
            }
        });
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        element.removeAnnotation(Assertion.class);
        if (isNeo4jImplementation && FIND_BY_CAPITAL.contains(element.getName()) && element.getOwningType().getName().equals(COUNTRIES)) {
            // Property graphs have no embedded objects, so these finders have no capital.name to compile against
            element.removeAnnotation(Find.class);
            element.removeAnnotation(OrderBy.class);
            element.annotate(Query.class, builder -> builder.value(NO_CAPITAL_QUERY));
        }
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (isJdbcImplementation || isMongoDBImplementation || isNeo4jImplementation) {
            if (element.getOwningType().hasStereotype(Entity.class)) {
                element.annotate(Nullable.class);
            }
            if (element.hasStereotype(ElementCollection.class)) {
                element.annotate(Transient.class);
            }
        }
        if (isNeo4jImplementation && element.getName().equals(COUNTRY_CAPITAL) && element.getOwningType().getName().equals(COUNTRY)) {
            // Property graphs have no embedded objects and the Cypher builder rejects @Embedded. Without the embedded
            // City the Countries repository compiles, so only the tests that read a capital are disabled
            element.annotate(Transient.class);
        }
        if (isMongoDBImplementation && element.getType().getName().equals("char")) {
            MongoUtils.bson(element);
        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        introspected = false;
    }
}
