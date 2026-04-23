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
package io.micronaut.data.processor.jpa.metamodel.visitor;


import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;

import javax.annotation.processing.SupportedOptions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static io.micronaut.data.processor.jpa.metamodel.JpaMetamodelProcessor.*;

/**
 * Jpa static meta model annotation processor visitor.
 */
@Internal
@SupportedOptions(JPA_METAMODEL_ENABLED_FLAG)
public final class JpaMetamodelProcessorVisitor implements TypeElementVisitor<Object, Object> {

    /**
     * Map of already processed entities.
     */
    private final Set<String> processed = new HashSet<>();

    /**
     * Source Persistent entity registry.
     */
    private final Map<String, SourcePersistentEntity> entityMap = new HashMap<>();

    /**
     * Persistent Entity resolver.
     */
    private final Function<ClassElement, SourcePersistentEntity> entityResolver = new Function<>() {
        @Override
        public SourcePersistentEntity apply(ClassElement classElement) {
            return entityMap.computeIfAbsent(classElement.getName(), s -> new SourcePersistentEntity(classElement, this));
        }
    };

    /**
     * Jakarta persistent dependency exists in compilation classpath flag.
     */
    @Nullable
    private Boolean jakartaPersistencePresent;

    /**
     * Default constructor.
     */
    public JpaMetamodelProcessorVisitor() {
    }

    /**
     * Supported Jakarta annotation names.
     * @return Set of strings of supported Jakarta annotation names
     */
    @Override
    public Set<String> getSupportedAnnotationNames() {
        return SUPPORTED_ANNOTATIONS;
    }

    /**
     * @param element class element
     * @param context visitor context
     */
    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!isEnabled(context) ||
            !supportedClass(element) ||
            !jakartaPersistenceIsPresent(context, element) ||
            processed.contains(element.getName())) {
            return;
        }

        SourcePersistentEntity persistentEntity = entityResolver.apply(element);

        try {
            ClassDef.ClassDefBuilder builder = createJpaMetaModelClassDefBuilder(element.getPackageName(), ClassTypeDef.of(persistentEntity.getType()), persistentEntity);
            ClassDef builderDef = builder.build();
            SourceGenerator sourceGenerator = SourceGenerators.findByLanguage(context.getLanguage()).orElse(null);
            if (sourceGenerator == null) {
                return;
            }
            processed.add(element.getName());
            sourceGenerator.write(builderDef, context, element);
        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new ProcessingException(element, "Failed to generate a @" + JAKARTA_STATIC_METAMODEL + ": " + message, e);
        }
    }

    /**
     * @param visitorContext visitor context
     */
    @Override
    public void start(VisitorContext visitorContext) {
        this.processed.clear();
    }

    /**
     * @return Visitor kind
     */
    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(JPA_METAMODEL_ENABLED_FLAG);
    }

    private boolean isEnabled(VisitorContext context) {
        return Boolean.parseBoolean(context.getOptions().getOrDefault(JPA_METAMODEL_ENABLED_FLAG, "true"));
    }

    private boolean jakartaPersistenceIsPresent(VisitorContext context, ClassElement element) {
        if (jakartaPersistencePresent != null) {
            return jakartaPersistencePresent;
        }
        if (context.getClassElement(JAKARTA_ENTITY).isPresent()) {
            jakartaPersistencePresent = true;
            return true;
        }
        context.warn("Jakarta Persistence API not found on compilation classpath; skipping metamodel generation.", element);
        jakartaPersistencePresent = false;
        return false;
    }
}
