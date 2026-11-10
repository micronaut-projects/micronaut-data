/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.document.processor

import io.micronaut.annotation.processing.visitor.JavaClassElement
import io.micronaut.data.document.processor.visitor.DocumentSerdeVisitor
import io.micronaut.data.document.serde.IdDeserializer
import io.micronaut.inject.ast.ClassElement
import io.micronaut.serde.annotation.SerdeableGenerated
import io.micronaut.serde.config.annotation.SerdeConfig

class DocumentSerdeVisitorSpec extends AbstractDataSpec {

    /**
     * Runs the visitor on the class with the context of the compilation that built it.
     */
    private ClassElement visit(String source) {
        JavaClassElement element = (JavaClassElement) buildClassElement(source)
        new DocumentSerdeVisitor().visitClass(element, element.@visitorContext)
        return element
    }

    private static String deserializer(ClassElement element, String property) {
        return element.beanProperties.find { it.name == property }.stringValue(SerdeConfig, SerdeConfig.DESERIALIZER_CLASS).orElse(null)
    }

    void "a generated identity of a supertype of String reads with the identity deserializer"() {
        when:
        ClassElement element = visit('''
package test;

import io.micronaut.data.annotation.*;

@MappedEntity
class Book {
    @Id
    @GeneratedValue
    private CharSequence id;
    private String title;

    public CharSequence getId() { return id; }
    public void setId(CharSequence id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
''')

        then:
        deserializer(element, 'id') == IdDeserializer.name
        deserializer(element, 'title') == null
    }

    void "a String or assigned identity keeps the default deserializer"() {
        when:
        ClassElement element = visit('''
package test;

import io.micronaut.data.annotation.*;

@MappedEntity
class Author {
    @Id
    @GeneratedValue
    private String id;
    @Id
    private Object other;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
''')

        then:
        deserializer(element, 'id') == null
    }

    void "field access with a boolean getter named like the field skips the generated serde"() {
        when:
        ClassElement element = visit('''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.*;

@MappedEntity
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Flag {
    @Id
    public String id;
    public boolean isActive;
    public Boolean isArchived;
    public boolean is;
    public boolean issue;
    public String isName;

    public boolean isActive() { return isActive; }
}
''')

        then:
        element.booleanValue(SerdeableGenerated, 'skip').orElse(false)
    }

    void "field access without such a getter keeps the generated serde"() {
        when:
        ClassElement element = visit('''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.*;

@Embeddable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Plain {
    public boolean isActive;
    public String name;
}
''')

        then:
        !element.booleanValue(SerdeableGenerated, 'skip').orElse(false)
    }
}
