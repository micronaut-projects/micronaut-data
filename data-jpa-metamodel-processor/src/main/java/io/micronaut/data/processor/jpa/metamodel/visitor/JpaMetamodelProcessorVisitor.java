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
import io.micronaut.data.processor.jpa.metamodel.JpaMetamodelProcessor;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Jpa static meta model annotation processor visitor.
 */
@Internal
public final class JpaMetamodelProcessorVisitor implements TypeElementVisitor<Object, Object> {

    private final Set<String> processed = new HashSet<>();

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
        return JpaMetamodelProcessor.SUPPORTED_JAKARTA_ANNOTATIONS;
    }

    /**
     * @param element class element
     * @param context visitor context
     */
    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!JpaMetamodelProcessor.supportedClass(element) ||
            processed.contains(element.getName())) {
            return;
        }
        try {
            List<PropertyElement> properties = element.getBeanProperties();
            ClassTypeDef elementType = ClassTypeDef.of(element);
            ClassDef.ClassDefBuilder builder = JpaMetamodelProcessor.createJpaMetaModelClassDefBuilder(element.getPackageName(), elementType, element.getSuperType(), properties);
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
            throw new ProcessingException(element, "Failed to generate a @" + JpaMetamodelProcessor.JAKARTA_STATIC_METAMODEL + ": " + message, e);
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

}
