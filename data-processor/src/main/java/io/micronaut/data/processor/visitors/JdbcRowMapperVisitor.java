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
package io.micronaut.data.processor.visitors;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.InstantiateWithDefaultConstructor;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.sql.JoinColumns;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.processor.model.SourceAssociation;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Generates {@code <Entity>$JdbcRowMapper} for every {@link MappedEntity}: an implementation of
 * {@code io.micronaut.data.jdbc.mapper.GeneratedRowMapper} that reads one row of a {@link ResultSet} into the
 * entity with the typed getters by column index, written by the Sourcegen generator registered for the language
 * being compiled. The mapper is registered as a Micronaut service of that interface, which is how
 * {@code GeneratedRowMappers} finds it at runtime and how a native image knows to include it.
 *
 * <p>The columns are the ones a query returning the entity without joins selects, as
 * {@link PersistentEntityUtils#traversePersistentProperties} lists them: the identity, the version, the persistent
 * properties, the leaves of embedded values and the identity of single ended associations, named by the entity's
 * naming strategy. Each column is read into a typed local, then the objects are built innermost first: through the
 * creator the bean introspection uses when it has parameters, through setters otherwise. The values and the
 * null handling are those of the flat entity mapper of {@code data-jdbc}, which is the mapper that delegates to
 * the generated class: a value the getter does not return in the property type (an enum, a java.time type) goes
 * through the conversion service, a property with an attribute converter is read in its persisted type and
 * converted by the converter with the property's conversion context, a SQL NULL read for a primitive leaves the property at its initial value, a
 * non-null constructor argument fails, an association target whose identity is null is null, and a nullable
 * embedded value without a single column value is null.</p>
 *
 * <p>Generation is on for Java entities whenever data-jdbc is on the compile classpath; the processor option
 * {@value #OPTION_ENABLED} set to {@code false} turns it off, and {@value #OPTION_REPORT} set to {@code true}
 * reports every entity no mapper is generated for. A failure while generating a mapper is a warning and the
 * entity keeps the runtime mappers; the option is the escape hatch should a generated source itself not
 * compile.</p>
 *
 * <p>No mapper is generated for an entity the runtime cannot read exactly that
 * way: a composite identity, a JSON column, a property type without a typed read (which includes arrays and
 * custom types the conversion service converts), an attribute converter that reads the result set itself or
 * persists such a type, a join column override, an identity or version that cannot be set, or a constructor
 * argument that is not a persistent property. Such entities keep the flat or the general mapper at runtime.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class JdbcRowMapperVisitor implements TypeElementVisitor<MappedEntity, Object> {

    static final String SUFFIX = "$JdbcRowMapper";
    /**
     * The processor option that turns the generation off ({@code false}).
     */
    static final String OPTION_ENABLED = "micronaut.data.jdbc.generatedRowMappers";
    /**
     * The processor option that reports every entity no mapper is generated for, with the reason ({@code true}).
     */
    static final String OPTION_REPORT = "micronaut.data.jdbc.generatedRowMappers.report";
    static final String MAPPER_INTERFACE = "io.micronaut.data.jdbc.mapper.GeneratedRowMapper";
    static final String SUPPORT = "io.micronaut.data.jdbc.mapper.GeneratedRowMapperSupport";

    private static final ClassTypeDef MAPPER_TYPE = ClassTypeDef.of(MAPPER_INTERFACE);
    private static final ClassTypeDef SUPPORT_TYPE = ClassTypeDef.of(SUPPORT);
    private static final ClassTypeDef RESULT_SET = ClassTypeDef.of(ResultSet.class);
    private static final ClassTypeDef SQL_EXCEPTION = ClassTypeDef.of(SQLException.class);
    private static final ClassTypeDef DATA_ACCESS_EXCEPTION = ClassTypeDef.of(DataAccessException.class);
    private static final ClassTypeDef CONVERSION_SERVICE = ClassTypeDef.of(ConversionService.class);
    private static final ClassTypeDef CONTEXT_TYPE = ClassTypeDef.of("io.micronaut.data.jdbc.mapper.GeneratedRowMapperContext");
    private static final String RESULT_READER_CONVERTER = "io.micronaut.data.model.runtime.convert.ResultReaderAttributeConverter";
    private static final ClassTypeDef BIG_DECIMAL = ClassTypeDef.of(BigDecimal.class);
    private static final ClassTypeDef TIMESTAMP = ClassTypeDef.of(Timestamp.class);
    private static final ClassTypeDef DATE = ClassTypeDef.of(java.sql.Date.class);
    private static final ClassTypeDef TIME = ClassTypeDef.of(java.sql.Time.class);

    private final Map<String, SourcePersistentEntity> entities = new HashMap<>();
    private final Function<ClassElement, SourcePersistentEntity> entityResolver = new Function<>() {
        @Override
        public SourcePersistentEntity apply(ClassElement classElement) {
            return entities.computeIfAbsent(classElement.getName(), n -> new SourcePersistentEntity(classElement, this));
        }
    };

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(MappedEntity.class.getName());
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(OPTION_ENABLED, OPTION_REPORT);
    }

    @Override
    public int getOrder() {
        // Visitors run in descending order: after MappedEntityVisitor, whose mapping defaults (the converter and
        // the data type of every property) the model reads from the elements
        return MappedEntityVisitor.POSITION - 1;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.isAbstract() || element.isInterface() || element.isEnum() || !element.hasAnnotation(MappedEntity.class)) {
            return;
        }
        if (context.getLanguage() != VisitorContext.Language.JAVA) {
            // Only the Java generator's rendering of the model is tested; Kotlin needs a KSP test before it is enabled
            return;
        }
        Map<String, String> options = context.getOptions();
        if ("false".equalsIgnoreCase(options.get(OPTION_ENABLED))) {
            return;
        }
        if (context.getClassElement(MAPPER_INTERFACE).isEmpty()) {
            // Only a project with data-jdbc on its classpath can use the mapper
            return;
        }
        SourceGenerator sourceGenerator = sourceGenerator(context);
        if (sourceGenerator == null) {
            return;
        }
        boolean report = "true".equalsIgnoreCase(options.get(OPTION_REPORT));
        try {
            SourcePersistentEntity entity = entityResolver.apply(element);
            Model model = Model.of(entity, context, report);
            if (model == null) {
                return;
            }
            String name = element.getName() + SUFFIX;
            sourceGenerator.write(generate(name, element, model), context, element);
            context.visitServiceDescriptor(MAPPER_INTERFACE, name, element);
        } catch (RuntimeException e) {
            if (isPostponement(e)) {
                // A lookup that names a type generated later in this compilation: the entity keeps the runtime
                // mappers rather than postponing the element, which would leave the repositories of this round
                // half processed
                if (report) {
                    context.info("No JDBC row mapper generated for " + element.getName() + ": a type it refers to is generated later in this compilation. Rows are mapped at runtime.", element);
                }
                return;
            }
            // The mapper is an optimization: a failure to generate it must not fail the build
            context.warn("No JDBC row mapper generated for " + element.getName() + ": " + e + ". Rows are mapped at runtime.", element);
        }
    }

    private static boolean isPostponement(RuntimeException e) {
        // The Java processor's PostponeToNextRoundException belongs to inject-java, which is only present when
        // compiling Java, so it is recognized by name
        return e instanceof ElementPostponedToNextRoundException || e.getClass().getSimpleName().equals("PostponeToNextRoundException");
    }

    /**
     * @return The Sourcegen generator of the language being compiled, or {@code null} when there is none or
     * Sourcegen is not on the classpath the visitor is loaded from (the Groovy compiler loads visitors from the
     * compile classpath, where a processor's own dependencies need not be)
     */
    @Nullable
    private static SourceGenerator sourceGenerator(VisitorContext context) {
        try {
            return SourceGenerators.findByLanguage(context.getLanguage()).orElse(null);
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    private static ClassDef generate(String name, ClassElement element, Model model) {
        ClassTypeDef entityType = ClassTypeDef.of(element);
        List<ExpressionDef> columns = new ArrayList<>(model.leaves.size());
        List<ExpressionDef> paths = new ArrayList<>(model.leaves.size());
        for (Leaf leaf : model.leaves) {
            columns.add(ExpressionDef.constant(leaf.column()));
            paths.add(ExpressionDef.constant(leaf.path()));
        }
        return ClassDef.builder(name)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(TypeDef.parameterized(MAPPER_TYPE, entityType))
            .addMethod(MethodDef.constructor()
                .addModifiers(Modifier.PUBLIC)
                .build((aThis, params) -> aThis.superRef().invokeSuperConstructor()))
            .addMethod(MethodDef.builder("entityType")
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .returns(TypeDef.parameterized(ClassTypeDef.of(Class.class), entityType))
                .build((aThis, params) -> ExpressionDef.constant(entityType).returning()))
            .addMethod(MethodDef.builder("columnNames")
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .returns(TypeDef.STRING.array())
                .build((aThis, params) -> TypeDef.STRING.array().instantiate(columns).returning()))
            .addMethod(MethodDef.builder("propertyPaths")
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .returns(TypeDef.STRING.array())
                .build((aThis, params) -> TypeDef.STRING.array().instantiate(paths).returning()))
            .addMethod(MethodDef.builder("map")
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .returns(TypeDef.OBJECT)
                .addParameter("rs", RESULT_SET)
                .addParameter("columns", TypeDef.Primitive.INT.array())
                .addParameter("context", CONTEXT_TYPE)
                .addThrows(SQL_EXCEPTION)
                .build((aThis, params) -> new Body(params.get(0), params.get(1), params.get(2)).build(model.root)))
            .build();
    }

    private static Read getter(String name, TypeDef type) {
        return new Read(type, (rs, c, ctx, i) -> rs.invoke(name, type, c));
    }

    private static Read support(String name, Class<?> type) {
        ClassTypeDef typeDef = ClassTypeDef.of(type);
        return new Read(typeDef, (rs, c, ctx, i) -> SUPPORT_TYPE.invokeStatic(name, typeDef, rs, c));
    }

    private static ExpressionDef conversionService(VariableDef context) {
        return context.invoke("conversionService", CONVERSION_SERVICE);
    }

    /**
     * {@code GeneratedRowMapperSupport.convert(rs.getX(c), Type.class, context.conversionService())}: a value the
     * getter does not return in the property type, converted as the general mapper converts it, through the
     * conversion service; a single read of the column.
     */
    private static Read converted(String getter, TypeDef getterType, ClassTypeDef type) {
        return new Read(type, (rs, c, ctx, i) -> SUPPORT_TYPE.invokeStatic("convert", type, rs.invoke(getter, getterType, c), ExpressionDef.constant(type), conversionService(ctx)));
    }

    /**
     * {@code (Type) context.convertToEntityValue(i, <read of the persisted type>)}: a property with an attribute
     * converter, read with the typed getter of the persisted type and converted by the property's converter with
     * its conversion context, as the general mapper does, {@code null} included. A primitive property is read in
     * its wrapper and unboxed by the body.
     */
    private static Read attributeConverted(Read persisted, ClassElement propertyType) {
        TypeDef type = TypeDef.of(propertyType);
        ClassTypeDef boxed = type instanceof TypeDef.Primitive primitive ? primitive.wrapperType() : (ClassTypeDef) type;
        return new Read(boxed, (rs, c, ctx, i) -> ctx.invoke("convertToEntityValue", TypeDef.OBJECT, ExpressionDef.constant(i), persisted.read(rs, c, ctx, i)).cast(boxed));
    }

    /**
     * @return How a property of the type is read for the data type, or {@code null} when there is no typed read
     */
    @Nullable
    private static Read readFor(ClassElement type, DataType dataType) {
        if (type.isEnum()) {
            if (dataType != DataType.STRING) {
                return null;
            }
            return converted("getString", TypeDef.STRING, ClassTypeDef.of(type));
        }
        String name = type.isArray() ? type.getName() + "[]" : type.getName();
        return switch (dataType) {
            case STRING -> "java.lang.String".equals(name) ? getter("getString", TypeDef.STRING) : null;
            case LONG -> switch (name) {
                case "long" -> getter("getLong", TypeDef.Primitive.LONG);
                case "java.lang.Long" -> support("readLong", Long.class);
                default -> null;
            };
            case INTEGER -> switch (name) {
                case "int" -> getter("getInt", TypeDef.Primitive.INT);
                case "java.lang.Integer" -> support("readInteger", Integer.class);
                default -> null;
            };
            case SHORT -> switch (name) {
                case "short" -> getter("getShort", TypeDef.Primitive.SHORT);
                case "java.lang.Short" -> support("readShort", Short.class);
                default -> null;
            };
            case BYTE -> switch (name) {
                case "byte" -> getter("getByte", TypeDef.Primitive.BYTE);
                case "java.lang.Byte" -> support("readByte", Byte.class);
                default -> null;
            };
            case DOUBLE -> switch (name) {
                case "double" -> getter("getDouble", TypeDef.Primitive.DOUBLE);
                case "java.lang.Double" -> support("readDouble", Double.class);
                default -> null;
            };
            case FLOAT -> switch (name) {
                case "float" -> getter("getFloat", TypeDef.Primitive.FLOAT);
                case "java.lang.Float" -> support("readFloat", Float.class);
                default -> null;
            };
            case BOOLEAN -> switch (name) {
                case "boolean" -> getter("getBoolean", TypeDef.Primitive.BOOLEAN);
                case "java.lang.Boolean" -> support("readBoolean", Boolean.class);
                default -> null;
            };
            case CHARACTER -> switch (name) {
                // A primitive char is unboxed by the body
                case "char", "java.lang.Character" -> support("readCharacter", Character.class);
                default -> null;
            };
            case BIGDECIMAL -> switch (name) {
                case "java.math.BigDecimal" -> getter("getBigDecimal", BIG_DECIMAL);
                case "java.math.BigInteger" -> converted("getBigDecimal", BIG_DECIMAL, ClassTypeDef.of(BigInteger.class));
                default -> null;
            };
            case BYTE_ARRAY -> "byte[]".equals(name) ? getter("getBytes", TypeDef.of(byte[].class)) : null;
            case TIMESTAMP -> switch (name) {
                case "java.sql.Timestamp" -> getter("getTimestamp", TIMESTAMP);
                case "java.time.LocalDateTime" -> converted("getTimestamp", TIMESTAMP, ClassTypeDef.of(LocalDateTime.class));
                case "java.time.Instant" -> converted("getTimestamp", TIMESTAMP, ClassTypeDef.of(Instant.class));
                case "java.time.OffsetDateTime" -> converted("getTimestamp", TIMESTAMP, ClassTypeDef.of(OffsetDateTime.class));
                case "java.time.ZonedDateTime" -> converted("getTimestamp", TIMESTAMP, ClassTypeDef.of(ZonedDateTime.class));
                case "java.time.LocalTime" -> converted("getTimestamp", TIMESTAMP, ClassTypeDef.of(LocalTime.class));
                default -> null;
            };
            case DATE -> switch (name) {
                case "java.util.Date", "java.sql.Date" -> getter("getDate", DATE);
                case "java.time.LocalDate" -> converted("getDate", DATE, ClassTypeDef.of(LocalDate.class));
                default -> null;
            };
            case TIME -> switch (name) {
                case "java.sql.Time" -> getter("getTime", TIME);
                case "java.time.LocalTime" -> converted("getTime", TIME, ClassTypeDef.of(LocalTime.class));
                default -> null;
            };
            case UUID -> "java.util.UUID".equals(name)
                ? new Read(ClassTypeDef.of(UUID.class), (rs, c, ctx, i) -> SUPPORT_TYPE.invokeStatic("readUuid", ClassTypeDef.of(UUID.class), rs, c, conversionService(ctx)))
                : null;
            default -> null;
        };
    }

    /**
     * The creator the bean introspection instantiates the type with: for a record its declared {@code @Creator}
     * (a static factory, with or without parameters, or a constructor) else its canonical constructor; for any
     * other type the primary constructor, which is a static {@code @Creator} factory when there is one;
     * {@code null} for a no-argument constructor, which is {@code new}.
     */
    @Nullable
    private static MethodElement constructorOf(SourcePersistentEntity entity) {
        ClassElement type = entity.getClassElement();
        if (type.hasAnnotation(InstantiateWithDefaultConstructor.class)) {
            // The default creator, which is a static factory before a no-argument constructor
            MethodElement creator = type.getDefaultConstructor().orElse(null);
            return creator != null && creator.isStatic() ? creator : null;
        }
        if (type.isRecord()) {
            // A declared creator first, by stereotype as the introspection finds it (@Creator, Jackson's
            // @JsonCreator, ...). Enumerating the methods postpones the element when one of them names a type
            // generated later in this compilation; the entity then keeps the runtime mappers (see visitClass)
            MethodElement creator = type.getEnclosedElement(ElementQuery.ALL_METHODS.onlyDeclared().onlyStatic().annotated(am -> am.hasStereotype(Creator.class))).orElse(null);
            if (creator != null) {
                return creator;
            }
            creator = type.getEnclosedElement(ElementQuery.CONSTRUCTORS.annotated(am -> am.hasStereotype(Creator.class))).orElse(null);
            if (creator != null) {
                return creator.getParameters().length > 0 ? creator : null;
            }
            // Else the canonical constructor: the one with a parameter per component, whatever other constructors
            // exist (an extra no-argument constructor can be the primary one)
            Set<String> components = new HashSet<>();
            for (PersistentProperty property : entity.getPersistentProperties()) {
                components.add(property.getName());
            }
            if (entity.hasIdentity()) {
                components.add(entity.getIdentity().getName());
            }
            if (entity.hasVersion()) {
                components.add(entity.getVersion().getName());
            }
            for (MethodElement candidate : type.getEnclosedElements(ElementQuery.CONSTRUCTORS.filter(c -> !c.isPrivate()))) {
                Set<String> names = new HashSet<>();
                for (ParameterElement parameter : candidate.getParameters()) {
                    names.add(parameter.getName());
                }
                if (!names.isEmpty() && names.equals(components)) {
                    return candidate;
                }
            }
        }
        // The introspection's creator: a static @Creator factory, whatever its arity, or a @Creator constructor
        // before anything else
        MethodElement primary = type.getPrimaryConstructor().orElse(null);
        if (primary != null && primary.isStatic()) {
            return primary;
        }
        return primary != null && primary.getParameters().length > 0 ? primary : null;
    }

    private static SourcePersistentProperty property(Member member) {
        return member instanceof Leaf leaf ? leaf.property() : ((Node) member).association();
    }

    private static Element element(Association association) {
        return ((SourcePersistentProperty) association).getPropertyElement();
    }

    private static boolean isIdentityOrVersion(SourcePersistentEntity entity, SourcePersistentProperty property) {
        return (entity.hasIdentity() && entity.getIdentity().equals(property))
            || (entity.hasVersion() && entity.getVersion().equals(property));
    }

    @Nullable
    private static MethodElement writeMethod(SourcePersistentProperty property) {
        PropertyElement element = property.getPropertyElement();
        return element.getWriteMethod().filter(m -> !m.isPrivate()).orElse(null);
    }

    private static String path(List<Association> associations, PersistentProperty property) {
        StringBuilder sb = new StringBuilder();
        for (Association association : associations) {
            sb.append(association.getName()).append('.');
        }
        return sb.append(property.getName()).toString();
    }

    /**
     * The typed read of one column.
     *
     * @param type       The type of the value
     * @param expression Builds the read from the result set, the column, the context and the column index
     */
    private record Read(TypeDef type, Expression expression) {

        ExpressionDef read(VariableDef rs, ExpressionDef column, VariableDef context, int index) {
            return expression.apply(rs, column, context, index);
        }

        /**
         * Builds the read of a column.
         */
        @FunctionalInterface
        interface Expression {
            /**
             * @param rs      The result set
             * @param column  The ordinal
             * @param context The generated row mapper context
             * @param index   The index of the column in the mapper's columns
             * @return The value
             */
            ExpressionDef apply(VariableDef rs, ExpressionDef column, VariableDef context, int index);
        }
    }

    /**
     * Thrown while the model is built for an entity the mapper cannot be generated for: flow control to the one
     * place that reports the reason, so it carries no stack trace.
     */
    private static final class UnsupportedEntityException extends RuntimeException {
        private final transient Element element;

        UnsupportedEntityException(String message, Element element) {
            super(message, null, false, false);
            this.element = element;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    private sealed interface Member permits Leaf, Node {
    }

    /**
     * A column: one persistent property reached through zero or more associations.
     *
     * @param index    The position in the column name array
     * @param property The property
     * @param path     The property path from the entity
     * @param column   The column name
     * @param read     The typed read
     */
    private record Leaf(int index, SourcePersistentProperty property, String path, String column, Read read) implements Member {

        boolean isPrimitive() {
            return property.getType().isPrimitive() && !property.getType().isArray();
        }
    }

    /**
     * An object to build: the entity, an embedded value or the target of a single ended association holding its
     * identity.
     */
    private static final class Node implements Member {
        final SourcePersistentEntity entity;
        @Nullable
        final SourceAssociation association;
        final Map<String, Member> members = new LinkedHashMap<>();
        @Nullable
        MethodElement constructor;

        Node(SourcePersistentEntity entity, @Nullable SourceAssociation association) {
            this.entity = entity;
            this.association = association;
        }

        ClassElement type() {
            return entity.getClassElement();
        }

        SourceAssociation association() {
            return Objects.requireNonNull(association);
        }

        Member member(String name) {
            return Objects.requireNonNull(members.get(name));
        }

        Node child(Association association) {
            Member member = members.get(association.getName());
            if (member instanceof Node node) {
                return node;
            }
            SourceAssociation source = (SourceAssociation) association;
            Node node = new Node(source.getAssociatedEntity(), source);
            members.put(association.getName(), node);
            return node;
        }

        boolean isEmbedded() {
            return association instanceof Embedded;
        }

        /**
         * @return Whether the object stands for an association target that is null when its identity is
         */
        boolean isAssociationTarget() {
            return association != null && !isEmbedded();
        }

        boolean isRoot() {
            return association == null;
        }
    }

    /**
     * The columns of an entity and the tree of objects they build, resolved once per entity.
     */
    private static final class Model {
        final Node root;
        final List<Leaf> leaves = new ArrayList<>();

        private Model(Node root) {
            this.root = root;
        }

        /**
         * @return The model, or {@code null} after warning when no mapper can be generated for the entity
         */
        @Nullable
        static Model of(SourcePersistentEntity entity, VisitorContext context, boolean report) {
            try {
                Model model = new Model(new Node(entity, null));
                model.collect(entity, context);
                model.resolveConstructors(model.root);
                model.index(model.root);
                return model;
            } catch (UnsupportedEntityException e) {
                if (report) {
                    context.info("No JDBC row mapper generated for " + entity.getName() + ": " + e.getMessage() + ". Rows are mapped at runtime.", e.element);
                }
                return null;
            }
        }

        private void collect(SourcePersistentEntity entity, VisitorContext context) {
            if (entity.hasCompositeIdentity()) {
                throw new UnsupportedEntityException("the entity has a composite identity", entity.getClassElement());
            }
            PersistentEntityUtils.traversePersistentProperties(entity, true, true, (associations, property) -> {
                Node node = root;
                for (Association association : associations) {
                    if (!(association instanceof Embedded) && association.getAnnotationMetadata().hasAnnotation(JoinColumns.class)) {
                        throw new UnsupportedEntityException("the association " + association.getName() + " declares join columns", element(association));
                    }
                    node = node.child(association);
                }
                SourcePersistentProperty source = (SourcePersistentProperty) property;
                String path = path(associations, property);
                Read read = readOf(source, path, context);
                String alias = source.getAlias();
                if (alias != null && !alias.isEmpty() && !associations.isEmpty()) {
                    throw new UnsupportedEntityException("the property " + path + " has an alias", source.getPropertyElement());
                }
                String column = alias != null && !alias.isEmpty() ? alias : entity.getNamingStrategy().mappedName(associations, property);
                node.members.put(property.getName(), new Leaf(-1, source, path, column, read));
            });
        }

        /**
         * How a property is read: by the typed getter of its data type, through the conversion service when the
         * getter does not return the property type, and through its attribute converter when it has one. The
         * converter and the data type are read from the element, where {@link MappedEntityVisitor} recorded the ones
         * it resolved (from {@code @TypeDef} or the configured data converters); the source property cached the
         * members before that.
         */
        private static Read readOf(SourcePersistentProperty source, String path, VisitorContext context) {
            PropertyElement element = source.getPropertyElement();
            DataType dataType = element.enumValue(MappedProperty.class, "type", DataType.class).orElseGet(source::getDataType);
            if (dataType == DataType.JSON) {
                throw new UnsupportedEntityException("the property " + path + " is a JSON column", element);
            }
            String converter = element.stringValue(MappedProperty.class, "converter").filter(c -> !Object.class.getName().equals(c)).orElse(null);
            if (converter == null) {
                Read read = readFor(source.getType(), dataType);
                if (read == null) {
                    throw new UnsupportedEntityException("the property " + path + " of type " + source.getType().getName() + " has no typed read for data type " + dataType, element);
                }
                return read;
            }
            if (context.getClassElement(converter).filter(c -> c.isAssignable(RESULT_READER_CONVERTER)).isPresent()) {
                throw new UnsupportedEntityException("the attribute converter of the property " + path + " reads the result set itself", element);
            }
            ClassElement persistedType = element.stringValue(MappedProperty.class, "converterPersistedType").flatMap(context::getClassElement).orElse(null);
            Read persisted = persistedType == null ? null : readFor(persistedType, dataType);
            if (persisted == null) {
                throw new UnsupportedEntityException("the attribute converter of the property " + path + " persists a type without a typed read for data type " + dataType, element);
            }
            return attributeConverted(persisted, source.getType());
        }

        /**
         * Numbers the leaves that survived in traversal order, which is the order of the column name array.
         */
        private void index(Node node) {
            for (Map.Entry<String, Member> entry : node.members.entrySet()) {
                if (entry.getValue() instanceof Leaf leaf) {
                    Leaf indexed = new Leaf(leaves.size(), leaf.property(), leaf.path(), leaf.column(), leaf.read());
                    leaves.add(indexed);
                    entry.setValue(indexed);
                } else {
                    index((Node) entry.getValue());
                }
            }
        }

        /**
         * Picks the constructor of every object the way the bean introspection does and checks that every
         * argument is a column or an object of the model; a member that is neither an argument nor has a setter is
         * dropped, unless it is the identity or the version.
         */
        private void resolveConstructors(Node node) {
            ClassElement type = node.type();
            MethodElement constructor = constructorOf(node.entity);
            node.constructor = constructor;
            Set<String> arguments = new HashSet<>();
            if (constructor != null) {
                if (node.isAssociationTarget() && !(constructor.getParameters().length == 1 && node.members.size() == 1)) {
                    // The general mapper builds the target of an unjoined association from its identity alone
                    throw new UnsupportedEntityException("the target of the association " + node.association().getName() + " cannot be built from its identity alone", element(node.association()));
                }
                for (ParameterElement parameter : constructor.getParameters()) {
                    if (!node.members.containsKey(parameter.getName())) {
                        throw new UnsupportedEntityException("the constructor argument " + parameter.getName() + " of " + type.getName() + " is not a column", parameter);
                    }
                    arguments.add(parameter.getName());
                }
            }
            List<String> dropped = new ArrayList<>();
            for (Map.Entry<String, Member> entry : node.members.entrySet()) {
                if (arguments.contains(entry.getKey())) {
                    continue;
                }
                SourcePersistentProperty property = property(entry.getValue());
                if (writeMethod(property) == null) {
                    if (isIdentityOrVersion(node.entity, property)) {
                        throw new UnsupportedEntityException("the property " + property.getName() + " of " + type.getName() + " cannot be set", property.getPropertyElement());
                    }
                    dropped.add(entry.getKey());
                }
            }
            node.members.keySet().removeAll(dropped);
            for (Member member : node.members.values()) {
                if (member instanceof Node child) {
                    resolveConstructors(child);
                }
            }
        }
    }

    /**
     * Emits the body of {@code map}: the columns of each object read into locals, then the object built, innermost
     * first.
     */
    private static final class Body {
        private final VariableDef rs;
        private final VariableDef columns;
        private final VariableDef context;
        private final List<StatementDef> statements = new ArrayList<>();
        /**
         * The local holding the value of each column, in the property type (the wrapper for a primitive char).
         */
        private final Map<Leaf, VariableDef> values = new HashMap<>();
        /**
         * Whether the column of each leaf was NULL, and whether it was not.
         */
        private final Map<Leaf, ExpressionDef.ConditionExpressionDef> nulls = new HashMap<>();
        private final Map<Leaf, ExpressionDef.ConditionExpressionDef> presents = new HashMap<>();
        private int locals;

        Body(VariableDef rs, VariableDef columns, VariableDef context) {
            this.rs = rs;
            this.columns = columns;
            this.context = context;
        }

        StatementDef build(Node root) {
            VariableDef entity = construct(root);
            statements.add(entity.returning());
            return StatementDef.multi(statements);
        }

        private void read(Leaf leaf) {
            // int cN = GeneratedRowMapperSupport.column(columns, N): the Java source generator of Sourcegen 2.1,
            // which the core BOM pins, renders no array element expression
            StatementDef.DefineAndAssign column = SUPPORT_TYPE.invokeStatic("column", TypeDef.Primitive.INT, columns, ExpressionDef.constant(leaf.index()))
                .newLocal("c" + leaf.index());
            statements.add(column);
            StatementDef.DefineAndAssign value = leaf.read().read(rs, column.variable(), context, leaf.index()).newLocal("v" + leaf.index());
            statements.add(value);
            VariableDef local = value.variable();
            values.put(leaf, local);
            if (leaf.read().type().isPrimitive()) {
                // boolean nN = vN == 0 && rs.wasNull(): the getters return zero for NULL
                ExpressionDef.ConditionExpressionDef zero = leaf.read().type().equals(TypeDef.Primitive.BOOLEAN)
                    ? local.isFalse()
                    : local.compare(ExpressionDef.ComparisonOperation.OpType.EQUAL_TO, ((TypeDef.Primitive) leaf.read().type()).constant(0));
                StatementDef.DefineAndAssign isNull = zero.and(rs.invoke("wasNull", TypeDef.Primitive.BOOLEAN).isTrue()).newLocal("n" + leaf.index());
                statements.add(isNull);
                nulls.put(leaf, isNull.variable().isTrue());
                presents.put(leaf, isNull.variable().isFalse());
            } else {
                nulls.put(leaf, local.isNull());
                presents.put(leaf, local.isNonNull());
            }
        }

        /**
         * @return The value of a leaf in the property type: the local, unboxed for a primitive property read in
         * its wrapper (a char, or a converted primitive)
         */
        private ExpressionDef value(Leaf leaf) {
            VariableDef local = Objects.requireNonNull(values.get(leaf));
            if (leaf.isPrimitive() && !leaf.read().type().isPrimitive()) {
                TypeDef.Primitive primitive = (TypeDef.Primitive) TypeDef.of(leaf.property().getType());
                return local.invoke(primitive.name() + "Value", primitive);
            }
            return local;
        }

        /**
         * Reads the columns of an object, builds its nested objects and the object itself, and returns the local
         * holding it.
         */
        private VariableDef construct(Node node) {
            Map<String, ExpressionDef> built = new LinkedHashMap<>();
            Map<String, ExpressionDef.ConditionExpressionDef> isNull = new LinkedHashMap<>();
            Map<String, ExpressionDef.ConditionExpressionDef> present = new LinkedHashMap<>();
            for (Map.Entry<String, Member> entry : node.members.entrySet()) {
                if (entry.getValue() instanceof Leaf leaf) {
                    read(leaf);
                    built.put(entry.getKey(), value(leaf));
                    isNull.put(entry.getKey(), nulls.get(leaf));
                    present.put(entry.getKey(), presents.get(leaf));
                } else {
                    VariableDef child = construct((Node) entry.getValue());
                    built.put(entry.getKey(), child);
                    isNull.put(entry.getKey(), child.isNull());
                    present.put(entry.getKey(), child.isNonNull());
                }
            }
            ClassTypeDef type = ClassTypeDef.of(node.type());
            MethodElement constructor = node.constructor;
            List<ExpressionDef> arguments = new ArrayList<>();
            Set<String> consumed = new HashSet<>();
            if (constructor != null) {
                for (ParameterElement parameter : constructor.getParameters()) {
                    arguments.add(Objects.requireNonNull(built.get(parameter.getName())));
                    consumed.add(parameter.getName());
                }
            }
            ExpressionDef instance;
            if (constructor == null) {
                instance = type.instantiate();
            } else if (constructor.isStatic()) {
                // A static @Creator factory, called with its arguments or none
                instance = type.invokeStatic(constructor, arguments);
            } else {
                instance = type.instantiate(constructor, arguments);
            }
            StatementDef.DefineAndAssign object;
            if (node.isRoot()) {
                // A required argument that is NULL fails as in the general mapper
                if (constructor != null) {
                    for (ParameterElement parameter : constructor.getParameters()) {
                        SourcePersistentProperty property = property(node.member(parameter.getName()));
                        if (!property.isOptional()) {
                            statements.add(condition(isNull, parameter.getName()).doIf(DATA_ACCESS_EXCEPTION.instantiate(
                                ExpressionDef.constant("Null value read for non-null constructor argument [" + parameter.getName() + "] of type: " + node.type().getName())
                            ).doThrow()));
                        }
                    }
                }
                object = instance.newLocal("o" + locals++);
                statements.add(object);
                statements.addAll(setters(node, object.variable(), built, present, consumed));
                return object.variable();
            }
            // An embedded value or an association target exists under a condition
            object = ExpressionDef.nullValue().cast(type).newLocal("o" + locals++);
            statements.add(object);
            VariableDef local = object.variable();
            ExpressionDef.ConditionExpressionDef exists;
            if (node.isAssociationTarget()) {
                // The target exists when its identity is not null
                exists = present.values().iterator().next();
            } else if (node.association().isOptional()) {
                // A nullable embedded value is null when none of its columns has a value
                exists = null;
                for (ExpressionDef.ConditionExpressionDef p : present.values()) {
                    exists = exists == null ? p : exists.or(p);
                }
            } else {
                // A required embedded value is null when a required constructor argument is null
                exists = null;
                if (constructor != null) {
                    for (ParameterElement parameter : constructor.getParameters()) {
                        SourcePersistentProperty property = property(node.member(parameter.getName()));
                        if (!property.isOptional()) {
                            ExpressionDef.ConditionExpressionDef p = condition(present, parameter.getName());
                            exists = exists == null ? p : exists.and(p);
                        }
                    }
                }
            }
            List<StatementDef> creation = new ArrayList<>();
            creation.add(local.assign(instance));
            creation.addAll(setters(node, local, built, present, consumed));
            statements.add(exists == null ? StatementDef.multi(creation) : exists.doIf(StatementDef.multi(creation)));
            return local;
        }

        private static ExpressionDef.ConditionExpressionDef condition(Map<String, ExpressionDef.ConditionExpressionDef> conditions, String name) {
            return Objects.requireNonNull(conditions.get(name));
        }

        /**
         * The statements setting the members that are not constructor arguments: a column when it is not NULL, a
         * nested object always.
         */
        private List<StatementDef> setters(Node node,
                                           VariableDef local,
                                           Map<String, ExpressionDef> built,
                                           Map<String, ExpressionDef.ConditionExpressionDef> present,
                                           Set<String> consumed) {
            List<StatementDef> setters = new ArrayList<>();
            for (Map.Entry<String, Member> entry : node.members.entrySet()) {
                if (consumed.contains(entry.getKey())) {
                    continue;
                }
                SourcePersistentProperty property = property(entry.getValue());
                ExpressionDef value = Objects.requireNonNull(built.get(entry.getKey()));
                StatementDef set = local.invoke(Objects.requireNonNull(writeMethod(property)), value);
                if (entry.getValue() instanceof Leaf) {
                    setters.add(condition(present, entry.getKey()).doIf(set));
                } else {
                    setters.add(set);
                }
            }
            return setters;
        }
    }
}
