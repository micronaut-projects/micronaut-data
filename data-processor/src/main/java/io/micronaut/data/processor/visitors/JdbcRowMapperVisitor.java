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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.InstantiateWithDefaultConstructor;
import io.micronaut.data.annotation.MappedEntity;
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
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.bytecode.ByteCodeWriter;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.OutputStream;
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
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Generates {@code <Entity>$JdbcRowMapper} for every {@link MappedEntity}: an implementation of
 * {@code io.micronaut.data.jdbc.mapper.GeneratedRowMapper} that reads one row of a {@link ResultSet} into the
 * entity with the typed getters by column index, written as bytecode so that Java, Kotlin and Groovy entities are
 * served alike and no source is generated. The mapper is registered as a Micronaut service of that interface, which
 * is how {@code GeneratedRowMappers} finds it at runtime and how a native image knows to include it.
 *
 * <p>The columns are the ones a query returning the entity without joins selects, as
 * {@link PersistentEntityUtils#traversePersistentProperties} lists them: the identity, the version, the persistent
 * properties, the leaves of embedded values and the identity of single ended associations, named by the entity's
 * naming strategy. Each column is read into a typed local, then the objects are built innermost first: through the
 * constructor the bean introspection uses when it has parameters, through setters otherwise. The values and the
 * null handling are those of the flat entity mapper of {@code data-jdbc}, which is the mapper that delegates to
 * the generated class: a SQL NULL read for a primitive leaves the property at its initial value, a non-null
 * constructor argument fails, an association target whose identity is null is null, and a nullable embedded value
 * without a single column value is null.</p>
 *
 * <p>No mapper is generated, with a warning naming the reason, for an entity the runtime cannot read exactly that
 * way: a composite identity, an attribute converter, a JSON column, a property type without a typed read (which
 * includes arrays and custom types the conversion service converts), a join column override, an identity or
 * version that cannot be set, or a constructor argument that is not a persistent property. Such entities keep the
 * flat or the general mapper at runtime.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class JdbcRowMapperVisitor implements TypeElementVisitor<MappedEntity, Object> {

    static final String SUFFIX = "$JdbcRowMapper";
    static final String MAPPER_INTERFACE = "io.micronaut.data.jdbc.mapper.GeneratedRowMapper";
    static final String SUPPORT = "io.micronaut.data.jdbc.mapper.GeneratedRowMapperSupport";

    private static final ClassTypeDef MAPPER_TYPE = ClassTypeDef.of(MAPPER_INTERFACE);
    private static final ClassTypeDef SUPPORT_TYPE = ClassTypeDef.of(SUPPORT);
    private static final ClassTypeDef RESULT_SET = ClassTypeDef.of(ResultSet.class);
    private static final ClassTypeDef SQL_EXCEPTION = ClassTypeDef.of(SQLException.class);
    private static final ClassTypeDef DATA_ACCESS_EXCEPTION = ClassTypeDef.of(DataAccessException.class);
    private static final ClassTypeDef BIG_DECIMAL = ClassTypeDef.of(BigDecimal.class);
    private static final ClassTypeDef BIG_INTEGER = ClassTypeDef.of(BigInteger.class);
    private static final ByteCodeWriter WRITER = new ByteCodeWriter(false, true);

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
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.isAbstract() || element.isInterface() || element.isEnum() || !element.hasAnnotation(MappedEntity.class)) {
            return;
        }
        if (context.getClassElement(MAPPER_INTERFACE).isEmpty()) {
            // Only a project with data-jdbc on its classpath can use the mapper
            return;
        }
        SourcePersistentEntity entity = entityResolver.apply(element);
        Model model = Model.of(entity, context);
        if (model == null) {
            return;
        }
        String name = element.getName() + SUFFIX;
        ClassDef classDef = generate(name, element, model);
        try (OutputStream os = context.visitClass(name, element)) {
            os.write(WRITER.write(classDef, null));
        } catch (IOException e) {
            throw new ProcessingException(element, "Failed to write the generated row mapper " + name + ": " + e.getMessage(), e);
        }
        context.visitServiceDescriptor(MAPPER_INTERFACE, name, element);
    }

    private static ClassDef generate(String name, ClassElement element, Model model) {
        ClassTypeDef entityType = ClassTypeDef.of(element);
        List<ExpressionDef> columns = new ArrayList<>(model.leaves.size());
        for (Leaf leaf : model.leaves) {
            columns.add(ExpressionDef.constant(leaf.column()));
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
            // Returns Object, the erasure of the interface method: the bytecode writer emits no bridge method
            .addMethod(MethodDef.builder("map")
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .returns(TypeDef.OBJECT)
                .addParameter("rs", RESULT_SET)
                .addParameter("columns", TypeDef.Primitive.INT.array())
                .addThrows(SQL_EXCEPTION)
                .build((aThis, params) -> new Body(params.get(0), params.get(1)).build(model.root)))
            .build();
    }

    private static Read getter(String name, TypeDef type) {
        return new Read(type, (rs, c) -> rs.invoke(name, type, c));
    }

    private static Read support(String name, Class<?> type) {
        ClassTypeDef typeDef = ClassTypeDef.of(type);
        return new Read(typeDef, (rs, c) -> SUPPORT_TYPE.invokeStatic(name, typeDef, rs, c));
    }

    /**
     * {@code T t = source; t == null ? null : convert(t)}.
     */
    private static Read derived(Read source, TypeDef type, Function<ExpressionDef, ExpressionDef> convert) {
        return new Read(type, (rs, c) -> {
            ExpressionDef read = source.read(rs, c);
            return read.isNull().doIfElse(ExpressionDef.nullValue().cast(type), convert.apply(read)).cast(type);
        });
    }

    /**
     * @return How the property is read, or {@code null} when its type has no typed read for its data type
     */
    @Nullable
    private static Read readFor(SourcePersistentProperty property) {
        ClassElement type = property.getType();
        DataType dataType = property.getDataType();
        if (type.isEnum()) {
            if (dataType != DataType.STRING) {
                return null;
            }
            ClassTypeDef enumType = ClassTypeDef.of(type);
            return derived(getter("getString", TypeDef.STRING), enumType, s -> enumType.invokeStatic("valueOf", enumType, s));
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
                case "java.math.BigInteger" -> derived(getter("getBigDecimal", BIG_DECIMAL), BIG_INTEGER, d -> d.invoke("toBigInteger", BIG_INTEGER));
                default -> null;
            };
            case BYTE_ARRAY -> "byte[]".equals(name) ? getter("getBytes", TypeDef.of(byte[].class)) : null;
            case TIMESTAMP -> switch (name) {
                case "java.sql.Timestamp" -> getter("getTimestamp", ClassTypeDef.of(Timestamp.class));
                case "java.time.LocalDateTime" -> support("readLocalDateTime", LocalDateTime.class);
                case "java.time.Instant" -> support("readInstant", Instant.class);
                case "java.time.OffsetDateTime" -> support("readOffsetDateTime", OffsetDateTime.class);
                case "java.time.ZonedDateTime" -> support("readZonedDateTime", ZonedDateTime.class);
                case "java.time.LocalTime" -> support("readLocalTimeOfTimestamp", LocalTime.class);
                default -> null;
            };
            case DATE -> switch (name) {
                case "java.util.Date", "java.sql.Date" -> getter("getDate", ClassTypeDef.of(java.sql.Date.class));
                case "java.time.LocalDate" -> support("readLocalDate", LocalDate.class);
                default -> null;
            };
            case TIME -> switch (name) {
                case "java.sql.Time" -> getter("getTime", ClassTypeDef.of(java.sql.Time.class));
                case "java.time.LocalTime" -> support("readLocalTime", LocalTime.class);
                default -> null;
            };
            case UUID -> "java.util.UUID".equals(name) ? support("readUuid", UUID.class) : null;
            default -> null;
        };
    }

    /**
     * The constructor the bean introspection instantiates the type with: the canonical constructor of a record,
     * else the primary constructor; {@code null} for a no-argument one.
     */
    @Nullable
    private static MethodElement constructorOf(ClassElement type) {
        if (type.isRecord()) {
            // The canonical constructor: the one with a parameter per component, whatever other constructors exist
            Set<String> components = new HashSet<>();
            for (PropertyElement property : type.getBeanProperties()) {
                components.add(property.getName());
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
        return type.getPrimaryConstructor().filter(c -> c.getParameters().length > 0).orElse(null);
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

    @Nullable
    private static FieldElement writableField(SourcePersistentProperty property) {
        PropertyElement element = property.getPropertyElement();
        return element.getField().filter(f -> !f.isPrivate() && !f.isFinal()).orElse(null);
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
     * @param expression Builds the read from the result set and the column
     */
    private record Read(TypeDef type, BiFunction<VariableDef, ExpressionDef, ExpressionDef> expression) {

        ExpressionDef read(VariableDef rs, ExpressionDef column) {
            return expression.apply(rs, column);
        }
    }

    /**
     * Thrown while the model is built for an entity the mapper cannot be generated for.
     */
    private static final class Unsupported extends RuntimeException {
        private final transient Element element;

        Unsupported(String message, Element element) {
            super(message, null, false, false);
            this.element = element;
        }
    }

    private interface Member {
    }

    /**
     * A column: one persistent property reached through zero or more associations.
     *
     * @param index    The position in the column name array
     * @param property The property
     * @param column   The column name
     * @param read     The typed read
     */
    private record Leaf(int index, SourcePersistentProperty property, String column, Read read) implements Member {

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
        static Model of(SourcePersistentEntity entity, VisitorContext context) {
            try {
                Model model = new Model(new Node(entity, null));
                model.collect(entity);
                model.resolveConstructors(model.root);
                model.index(model.root);
                return model;
            } catch (Unsupported e) {
                context.info("No JDBC row mapper generated for " + entity.getName() + ": " + e.getMessage() + ". Rows are mapped at runtime.", e.element);
                return null;
            }
        }

        private void collect(SourcePersistentEntity entity) {
            if (entity.hasCompositeIdentity()) {
                throw new Unsupported("the entity has a composite identity", entity.getClassElement());
            }
            PersistentEntityUtils.traversePersistentProperties(entity, true, true, (associations, property) -> {
                Node node = root;
                for (Association association : associations) {
                    if (!(association instanceof Embedded) && association.getAnnotationMetadata().hasAnnotation(JoinColumns.class)) {
                        throw new Unsupported("the association " + association.getName() + " declares join columns", element(association));
                    }
                    node = node.child(association);
                }
                SourcePersistentProperty source = (SourcePersistentProperty) property;
                String path = path(associations, property);
                if (source.getConverterClassName() != null) {
                    throw new Unsupported("the property " + path + " has an attribute converter", source.getPropertyElement());
                }
                if (source.getDataType() == DataType.JSON) {
                    throw new Unsupported("the property " + path + " is a JSON column", source.getPropertyElement());
                }
                Read read = readFor(source);
                if (read == null) {
                    throw new Unsupported("the property " + path + " of type " + source.getType().getName() + " has no typed read for data type " + source.getDataType(), source.getPropertyElement());
                }
                String alias = source.getAlias();
                if (alias != null && !alias.isEmpty() && !associations.isEmpty()) {
                    throw new Unsupported("the property " + path + " has an alias", source.getPropertyElement());
                }
                String column = alias != null && !alias.isEmpty() ? alias : entity.getNamingStrategy().mappedName(associations, property);
                node.members.put(property.getName(), new Leaf(-1, source, column, read));
            });
        }

        /**
         * Numbers the leaves that survived in traversal order, which is the order of the column name array.
         */
        private void index(Node node) {
            for (Map.Entry<String, Member> entry : node.members.entrySet()) {
                if (entry.getValue() instanceof Leaf leaf) {
                    Leaf indexed = new Leaf(leaves.size(), leaf.property(), leaf.column(), leaf.read());
                    leaves.add(indexed);
                    entry.setValue(indexed);
                } else {
                    index((Node) entry.getValue());
                }
            }
        }

        /**
         * Picks the constructor of every object the way the bean introspection does and checks that every
         * argument is a column or an object of the model; a member that is neither an argument nor settable is
         * dropped, unless it is the identity or the version.
         */
        private void resolveConstructors(Node node) {
            ClassElement type = node.type();
            MethodElement constructor = type.hasAnnotation(InstantiateWithDefaultConstructor.class)
                ? null
                : constructorOf(type);
            node.constructor = constructor;
            Set<String> arguments = new HashSet<>();
            if (constructor != null) {
                if (node.isAssociationTarget() && !(constructor.getParameters().length == 1 && node.members.size() == 1)) {
                    // The general mapper builds the target of an unjoined association from its identity alone
                    throw new Unsupported("the target of the association " + node.association().getName() + " cannot be built from its identity alone", element(node.association()));
                }
                for (ParameterElement parameter : constructor.getParameters()) {
                    if (!node.members.containsKey(parameter.getName())) {
                        throw new Unsupported("the constructor argument " + parameter.getName() + " of " + type.getName() + " is not a column", parameter);
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
                if (writeMethod(property) == null && writableField(property) == null) {
                    if (isIdentityOrVersion(node.entity, property)) {
                        throw new Unsupported("the property " + property.getName() + " of " + type.getName() + " cannot be set", property.getPropertyElement());
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

        Body(VariableDef rs, VariableDef columns) {
            this.rs = rs;
            this.columns = columns;
        }

        StatementDef build(Node root) {
            VariableDef entity = construct(root);
            statements.add(entity.returning());
            return StatementDef.multi(statements);
        }

        private void read(Leaf leaf) {
            ExpressionDef column = columns.arrayElement(leaf.index());
            StatementDef.DefineAndAssign value = leaf.read().read(rs, column).newLocal("v" + leaf.index());
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
         * @return The value of a leaf in the property type: the local, unboxed for a primitive char
         */
        private ExpressionDef value(Leaf leaf) {
            VariableDef local = Objects.requireNonNull(values.get(leaf));
            if (leaf.isPrimitive() && !leaf.read().type().isPrimitive()) {
                return local.invoke("charValue", TypeDef.Primitive.CHAR);
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
            ExpressionDef instance = constructor == null ? type.instantiate() : type.instantiate(constructor, arguments);
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
                MethodElement setter = writeMethod(property);
                StatementDef set = setter != null
                    ? local.invoke(setter, value)
                    : local.field(Objects.requireNonNull(writableField(property))).assign(value);
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
