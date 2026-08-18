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
package io.micronaut.data.nitrite.runtime.mapping;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.NameUtils;
import io.micronaut.data.nitrite.runtime.NitriteOperationsHelper;
import io.micronaut.data.nitrite.runtime.ValueConverter;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterUtils;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.serde.ObjectMapper;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteId;
import org.dizitart.no2.filters.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mapper for converting entities to Nitrite Documents and back.
 *
 * @since 5.2.0
 */
@Internal
public final class NitriteEntityMapper {

  /**
   * Canonical identity field name. Nitrite documents always store the identity under this name,
   * regardless of the entity's mapped identity name.
   */
  public static final String ID_FIELD = "id";

  private static final Logger LOG = LoggerFactory.getLogger(NitriteEntityMapper.class);
  private static final String GEOMETRY_CLASS = "org.locationtech.jts.geom.Geometry";

  private final ConversionService conversionService;
  private final ValueConverter valueConverter;
  private final @Nullable ObjectMapper serdeObjectMapper;
  private final RuntimeEntityRegistry runtimeEntityRegistry;
  private @Nullable NitriteOperationsHelper helper;
  private final @Nullable Class<?> geometryClass;
  private final ConcurrentHashMap<Class<?>, NitriteEntityMeta<?>> entityMetaCache = new ConcurrentHashMap<>();

  /**
   * Create a new mapper.
   *
   * <p><strong>Architecture:</strong> This mapper uses Micronaut Serde only when an
   * application provides a Serde {@link ObjectMapper}; otherwise it relies on
   * {@link ConversionService}, {@link BeanIntrospection}, and plain JDK JavaBean
   * reflection (see {@link #reflectToMap}).</p>
   *
   * @param conversionService the conversion service (for field-level conversions)
   * @param serdeObjectMapper the optional Micronaut Serde ObjectMapper
   * @param runtimeEntityRegistry the runtime entity registry
   */
  public NitriteEntityMapper(
      final ConversionService conversionService,
      final @Nullable ObjectMapper serdeObjectMapper,
      final RuntimeEntityRegistry runtimeEntityRegistry) {
    this.conversionService = conversionService;
    this.valueConverter = new ValueConverter(conversionService);
    this.serdeObjectMapper = serdeObjectMapper;
    this.runtimeEntityRegistry = runtimeEntityRegistry;
    this.geometryClass = ClassUtils.forName(GEOMETRY_CLASS, NitriteEntityMapper.class.getClassLoader()).orElse(null);
  }

  /**
   * Set the operations helper.
   * @param helper the helper
   */
  public void setHelper(NitriteOperationsHelper helper) {
      this.helper = helper;
  }

    /**
   * Convert a value to a format suitable for Nitrite Filters.
   * <p>
   * Delegates to {@link ValueConverter#toFilterValueStatic} for stateless conversions,
   * then falls back to entity-ID extraction via the runtime entity registry.
   *
   * @param value the raw value
   * @return the normalized value
   */
  public @Nullable Object toFilterValue(@Nullable Object value) {
    Object result = ValueConverter.toFilterValueStatic(value);
    if (!Objects.equals(result, value)) {
      return result;
    }
    if (value == null) {
      return null;
    }

    Class<?> clazz = value.getClass();
    // Skip expensive entity registry lookups for JDK classes
    if (clazz.getName().startsWith("java.")) {
        return value;
    }

    // If it's an entity, try to get its ID
    try {
        RuntimePersistentEntity<Object> entity = runtimeEntityRegistry.getEntity(castClass(clazz));
        if (entity != null) {
            RuntimePersistentProperty<Object> idProp = safeGetIdentity(entity);
            if (idProp != null) {
                Object idValue = idProp.getProperty().get(value);
                if (idValue != null) {
                    return toFilterValue(idValue);
                }
            }
        }
    } catch (RuntimeException e) {
        // Expected for values whose class isn't a registered entity (the common case for
        // this method); fall through and return the value itself.
        if (LOG.isDebugEnabled()) {
            LOG.debug("Best-effort entity ID extraction skipped for type {}: {}", clazz, e.getMessage());
        }
    }

    return value;
  }

  /**
   * Converts an arbitrary value (a {@link Document}, a {@link Map}, an {@code @Introspected}
   * POJO, or a plain JavaBean) to a {@link Document}, for callers that need to traverse an
   * intermediate value's fields (e.g. segment-by-segment path resolution on a query parameter).
   *
   * @param value the value to convert
   * @return the document representation of the value
   */
  public @Nullable Document convertValueToDocument(final @Nullable Object value) {
    return toDocumentValue(value);
  }

  /**
   * Convert a value to a format suitable for Nitrite Filters, considering property metadata.
   *
   * @param val the raw value
   * @return the normalized value
   */
  public @Nullable Object toNitriteFilterValue(final @Nullable Object val) {
    if (val == null) {
      return null;
    }
    if (val instanceof Document) {
      return val;
    }
    Class<?> clazz = val.getClass();
    if (!clazz.getName().startsWith("java.")) {
      Optional<BeanIntrospection<Object>> intro = BeanIntrospector.SHARED.findIntrospection(castClass(clazz));
      if (intro.isPresent()) {
        if (intro.get().hasAnnotation(MappedEntity.class)) {
          RuntimePersistentEntity<Object> entity = runtimeEntityRegistry.getEntity(castClass(clazz));
          RuntimePersistentProperty<Object> idProp = safeGetIdentity(entity);
          if (idProp != null) {
            Object idValue = idProp.getProperty().get(val);
            if (idValue != null) {
              return normalizeIdentityValue(idProp, idValue);
            }
          }
        } else {
          return toDocumentValue(val);
        }
      }
    }
    return toFilterValue(val);
  }

  /**
   * Extract the ID value from an entity.
   *
   * @param entity the entity instance
   * @param type the entity class
   * @return the ID value
   * @param <T> the entity type
   */
  public <T> @Nullable Object getEntityIdValue(final T entity, final Class<T> type) {
    RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
    RuntimePersistentProperty<T> idProp = safeGetIdentity(persistentEntity);
    if (idProp != null) {
      return idProp.getProperty().get(entity);
    }
    return null;
  }

  private static <E> @Nullable RuntimePersistentProperty<E> safeGetIdentity(RuntimePersistentEntity<E> persistentEntity) {
    if (!persistentEntity.hasIdentity() || persistentEntity.hasCompositeIdentity()) {
      return null;
    }
    return persistentEntity.getIdentity();
  }

    /**
   * Normalize a field name.
   *
   * @param field the field name
   * @param entity the entity metadata
   * @return the normalized field name
   */
  public String normalizeFieldName(final String field, @Nullable final RuntimePersistentEntity<?> entity) {
    return persistedPath(field, entity);
  }

  /**
   * Resolve a property path to the path the document is stored under. Every segment of a dotted
   * path is mapped through its own {@code @MappedProperty}, so a nested value is addressed by the
   * same path the mapper writes. A segment that is not a known property is kept verbatim.
   *
   * @param field the property name or dotted property path
   * @param entity the entity metadata, may be null
   * @return the persisted document path
   */
  public static String persistedPath(final String field, @Nullable final RuntimePersistentEntity<?> entity) {
    if (entity == null) {
      return "_id".equals(field) ? ID_FIELD : field;
    }
    if (field.indexOf('.') < 0) {
      RuntimePersistentProperty<?> idProperty = safeGetIdentity(entity);
      if (idProperty != null && (idProperty.getName().equals(field)
          || idProperty.getPersistedName().equals(field)
          || "_id".equals(field)
          || ID_FIELD.equals(field))) {
        return ID_FIELD;
      }
      RuntimePersistentProperty<?> prop = entity.getPropertyByName(field);
      if (prop != null) {
        return prop.getPersistedName();
      }
      return "_id".equals(field) ? ID_FIELD : field;
    }

    StringBuilder path = new StringBuilder();
    RuntimePersistentEntity<?> current = entity;
    for (String segment : field.split("\\.", -1)) {
      if (!path.isEmpty()) {
        path.append('.');
      }
      RuntimePersistentProperty<?> property = current == null ? null : current.getPropertyByName(segment);
      if (property == null) {
        path.append(segment);
        current = null;
        continue;
      }
      path.append(property.getPersistedName());
      current = property instanceof RuntimeAssociation<?> association ? association.getAssociatedEntity() : null;
    }
    return path.toString();
  }

  /**
   * Create a filter matching the entity ID.
   *
   * @param type the entity class
   * @param id the ID value
   * @return the Nitrite Filter
   * @param <T> the entity type
   */
  public <T> Filter idEqualsFilter(final Class<T> type, final @Nullable Object id) {
    NitriteEntityMeta<T> meta = getOrBuildMeta(type);
    return idEqualsFilter(meta, id);
  }

  /**
   * Create a filter matching the entity ID using pre-computed metadata.
   *
   * @param meta the pre-computed entity metadata
   * @param id the ID value
   * @return the Nitrite Filter
   * @param <T> the entity type
   */
  public <T> Filter idEqualsFilter(final NitriteEntityMeta<T> meta, final @Nullable Object id) {
    RuntimePersistentProperty<T> idProperty = meta.idProp();

    // In Nitrite, we consistently use ID_FIELD ("id") for the identity property.
    String idField = ID_FIELD;

    if (idProperty != null && id != null) {
      Object normalizedId = normalizeIdentityValue(idProperty, id);
      if (normalizedId instanceof Document idDoc) {
        return pair -> {
          Document doc = pair.getSecond();
          for (String field : idDoc.getFields()) {
            if (!Objects.equals(doc.get(field), idDoc.get(field))) {
              return false;
            }
          }
          return true;
        };
      }
    }
    return eqWithNumericCoercion(meta.persistentEntity(), idField, toFilterValue(id), idField);
  }

  /**
   * Create an equality filter with numeric type tolerance.
   *
   * @param entity the entity metadata
   * @param field the field name
   * @param value the comparison value
   * @param dottedPath the full path for logging
   * @param <E> the entity type
   * @return the Nitrite Filter
   */
  public <E> Filter eqWithNumericCoercion(final RuntimePersistentEntity<E> entity, final String field, final @Nullable Object value, final String dottedPath) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("eqWithNumericCoercion: field={}, value={}, type={}, dottedPath={}", field, value, (value != null ? value.getClass().getName() : "null"), dottedPath);
    }

    if (value == null) {
      return NitriteFilterUtils.isNullFilter(dottedPath);
    }
    Filter base = NitriteFilterUtils.eq(dottedPath, value);
    if (!(value instanceof Number n)) {
      return base;
    }

    if (entity != null) {
      RuntimePersistentProperty<E> property = entity.getPropertyByName(field);
      if (property == null) {
        for (RuntimePersistentProperty<E> p : entity.getPersistentProperties()) {
          if (p.getPersistedName().equals(field)) {
            property = p;
            break;
          }
        }
      }

      if (property != null) {
        Class<?> targetType = property.getType();
        if (Number.class.isAssignableFrom(targetType) || targetType.isPrimitive()) {
          Optional<?> converted = conversionService.convert(n, targetType);
          if (converted.isPresent()) {
            return NitriteFilterUtils.eq(dottedPath, converted.get());
          }
        }
        Filter precise = switch (targetType) {
          case Class<?> t when t == int.class   || t == Integer.class -> NitriteFilterUtils.eq(dottedPath, n.intValue());
          case Class<?> t when t == long.class  || t == Long.class    -> NitriteFilterUtils.eq(dottedPath, n.longValue());
          case Class<?> t when t == double.class || t == Double.class -> NitriteFilterUtils.eq(dottedPath, n.doubleValue());
          case Class<?> t when t == float.class || t == Float.class   -> NitriteFilterUtils.eq(dottedPath, n.floatValue());
          case Class<?> t when t == short.class || t == Short.class   -> NitriteFilterUtils.eq(dottedPath, n.shortValue());
          case Class<?> t when t == byte.class  || t == Byte.class    -> NitriteFilterUtils.eq(dottedPath, n.byteValue());
          default -> null;
        };
        if (precise != null) {
          return precise;
        }
      }
    }

    return switch (n) {
      case Integer _, Double _, Float _ -> base;
        default        -> Filter.or(base,
          NitriteFilterUtils.eq(dottedPath, n.longValue()),
          NitriteFilterUtils.eq(dottedPath, n.intValue()),
          NitriteFilterUtils.eq(dottedPath, n.doubleValue()));
    };
  }

  /**
   * Convert an entity to a Nitrite Document.
   *
   * @param entity the entity instance
   * @return the Nitrite Document
   * @param <T> the entity type
   */
  public <T> @Nullable Document toDocument(final @Nullable T entity) {
    if (entity == null) {
      return null;
    }
    return toDocumentInternal(entity, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private <T> @Nullable Document toDocumentInternal(final @Nullable T entity, final Set<Object> visited) {
    if (entity == null) {
        return null;
    }
    visited.add(entity);

    Document doc = convertToDocumentInternal(entity, visited);
    if (doc == null) {
        return null;
    }

    // Cache getEntity() result - called twice in original code, now only once
    RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(castClass(entity.getClass()));

    // Entities with @JsonProperty("_id") cause Jackson to serialize the id as "_id".
    // Nitrite reserves "_id" for NitriteId — rename user's id to its property name to avoid InvalidIdException.
    Object reservedId = doc.get("_id");
    if (reservedId != null && !(reservedId instanceof NitriteId)) {
      doc.remove("_id");
      RuntimePersistentProperty<T> idProp = safeGetIdentity(persistentEntity);
      String idField = idProp != null ? idProp.getName() : ID_FIELD;
      doc.put(idField, toFilterValue(reservedId));
    }

    RuntimePersistentProperty<T> idProperty = safeGetIdentity(persistentEntity);
    if (idProperty == null && persistentEntity.hasCompositeIdentity()) {
      // A composite identity has no single id property, and identity properties are not part of
      // getPersistentProperties(), so nothing else writes them. Without this the document holds only
      // the generated _id and the identity cannot be read back, filtered or sorted on.
      for (RuntimePersistentProperty<T> identityProperty : persistentEntity.getRuntimeIdentityProperties()) {
        Object identityValue = identityProperty.getProperty().get(entity);
        if (identityValue != null) {
          doc.put(identityProperty.getPersistedName(), toFilterValue(identityValue));
        }
      }
    }
    Object normalizedId = null;
    Object idValue = idProperty != null ? idProperty.getProperty().get(entity) : null;
    boolean embeddedIdProperty = idProperty != null && idProperty.isAnnotationPresent(EmbeddedId.class);
    if (idProperty != null && idValue != null && !embeddedIdProperty) {
      normalizedId = normalizeIdentityValue(idProperty, idValue);
      doc.put(ID_FIELD, normalizedId);
    }
    if (idProperty != null && embeddedIdProperty && idValue != null) {
      try {
        Document idDoc = toDocumentValue(idValue);
        if (idDoc != null) {
          Document persistedIdDoc = toPersistedDocument(idDoc);
          doc.put(ID_FIELD, idDoc);
          doc.put(idProperty.getName(), idDoc);
          doc.put(idProperty.getPersistedName(), persistedIdDoc);
          for (String field : idDoc.getFields()) {
            if (field.equals(idProperty.getName())) {
              continue;
            }
            Object fieldValue = toFilterValue(idDoc.get(field));
            doc.put(field, fieldValue);
            doc.put(NameUtils.camelToSnake(field), fieldValue);
          }
        }
      } catch (Exception ignored) {
        // If embedded ID processing fails, fall through to default normalization
      }
    } else if (normalizedId instanceof Document idDoc) {
      for (String field : idDoc.getFields()) {
        doc.put(field, toFilterValue(idDoc.get(field)));
      }
    }
    return doc;
  }

    /**
   * Build per-entity metadata: pre-filter writable properties and classify their serialization
   * strategy from the declared type. Called once per entity type; result is cached in
   * {@link #entityMetaCache}.
   *
   * @param <T> the entity type
   * @param persistentEntity the runtime persistent entity
   * @return the pre-computed entity metadata
   */
  @SuppressWarnings({"unchecked"})
  private <T> NitriteEntityMeta<T> buildEntityMeta(RuntimePersistentEntity<T> persistentEntity) {
    List<WritablePropertyMeta<T>> writableList = new ArrayList<>();
    List<WritablePropertyMeta<T>> mappedByList = new ArrayList<>();
    List<RuntimeAssociation<T>> cascadeList = new ArrayList<>();
    boolean hasBackRefs = false;

    for (RuntimePersistentProperty<T> prop : persistentEntity.getPersistentProperties()) {
      // Read-only (constructor-only) properties are still persisted: writing reads through the
      // getter, and skipping them would drop the state of an immutable entity from the document.
      if (prop.isAnnotationPresent(Transient.class)
          || prop.getProperty().isAnnotationPresent(Transient.class)) {
        continue;
      }

      String fieldName = prop.getPersistedName();
      PropertyStrategy strategy;
      String mappedByValue = null;
      BeanProperty<Object, Object> associatedIdProp = null;
      BeanProperty<Object, Object> backRefProp = null;
      List<CompositeJoinColumn> compositeJoinColumns = List.of();

      if (prop instanceof RuntimeAssociation<?> assoc) {
        mappedByValue = assoc.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
        if (mappedByValue != null) {
          strategy = PropertyStrategy.ASSOCIATION_MAPPED_BY;
          RuntimePersistentProperty<?> backPropRaw = assoc.getAssociatedEntity().getPropertyByName(mappedByValue);
          if (backPropRaw != null) {
            backRefProp = (BeanProperty<Object, Object>) backPropRaw.getProperty();
            hasBackRefs = true;
          }
        } else if (assoc.isEmbedded()) {
          strategy = PropertyStrategy.ASSOCIATION_EMBEDDED;
        } else {
          RuntimePersistentProperty<?> idPropRaw = safeGetIdentity(assoc.getAssociatedEntity());
          // An entity with a composite identity has no single id property, so idPropRaw is null.
          // We must not fall back to ASSOCIATION_EMBEDDED here; it remains an ID reference,
          // and its foreign keys are mapped explicitly via @JoinColumn (handled below).
          if (idPropRaw == null && !assoc.getAssociatedEntity().hasCompositeIdentity()) {
            strategy = PropertyStrategy.ASSOCIATION_EMBEDDED;
          } else {
            boolean isCollection = Iterable.class.isAssignableFrom(prop.getType());
            strategy = isCollection ? PropertyStrategy.ASSOCIATION_IDS_REF : PropertyStrategy.ASSOCIATION_ID_REF;
            if (idPropRaw != null) {
              associatedIdProp = (BeanProperty<Object, Object>) idPropRaw.getProperty();
            }

            // Composite foreign key: more than one @JoinColumn means the association also needs
            // its own local fields (matching NitriteQueryBuilderHelper's $lookup expectations)
            // mirroring the referenced properties on the associated entity, in addition to the
            // normal single-field ID reference used for eager hydration.
            List<AnnotationValue<JoinColumn>> joinColumnValues =
                assoc.getAnnotationMetadata().getAnnotationValuesByType(JoinColumn.class);
            if (joinColumnValues.size() > 1) {
              List<CompositeJoinColumn> list = new ArrayList<>();
              for (AnnotationValue<JoinColumn> jc : joinColumnValues) {
                String localName = jc.stringValue("name").orElse(fieldName);
                jc.stringValue("referencedColumnName").ifPresent(
                    referenced -> list.add(new CompositeJoinColumn(localName, referenced)));
              }
              compositeJoinColumns = List.copyOf(list);
            }
          }
        }
        // Pre-compute cascade-capable associations for PERSIST/ALL (for ALL associations, not just mappedBy)
        if (assoc.doesCascade(Relation.Cascade.ALL) || assoc.doesCascade(Relation.Cascade.PERSIST)) {
          cascadeList.add((RuntimeAssociation<T>) assoc);
        }
      } else {
        strategy = PropertyStrategy.classifyValueStrategy(geometryClass, prop.getType());
        if (strategy == PropertyStrategy.ENTITY_ID_REF) {
          try {
            RuntimePersistentEntity<Object> refEntity = runtimeEntityRegistry.getEntity(castClass(prop.getType()));
            if (refEntity != null) {
                RuntimePersistentProperty<Object> refIdentity = safeGetIdentity(refEntity);
                if (refIdentity != null) {
                    associatedIdProp = refIdentity.getProperty();
                }
            }
          } catch (Exception ignored) {
              // Best-effort identity lookup for ID-ref strategy
          }
        }
      }

      WritablePropertyMeta<T> meta = new WritablePropertyMeta<>(
          prop, fieldName, strategy, mappedByValue, associatedIdProp, backRefProp, compositeJoinColumns);
      if (strategy == PropertyStrategy.ASSOCIATION_MAPPED_BY) {
        mappedByList.add(meta);
      } else {
        writableList.add(meta);
      }
    }

    // Cache ID accessor for fast ID property access
    BeanProperty<T, Object> idAccessor = null;
    RuntimePersistentProperty<T> idProp = safeGetIdentity(persistentEntity);
    if (idProp != null) {
      idAccessor = idProp.getProperty();
    }

    RuntimePersistentProperty<T> versionProp = null;
    try {
      versionProp = persistentEntity.getVersion();
    } catch (IllegalStateException e) {
      // entity has no version – leave null
    }
    return new NitriteEntityMeta<>(
        List.copyOf(writableList),
        List.copyOf(mappedByList),
        idProp,
        versionProp,
        persistentEntity,
        List.copyOf(cascadeList),
        hasBackRefs,
        idAccessor);
  }

  /**
   * Return the cached {@link NitriteEntityMeta} for {@code type}, building it on first access.
   * After the first call per entity type, no registry or annotation lookups are performed.
   *
   * <p>Uses get-first pattern to avoid computeIfAbsent lock contention on cache hits.</p>
   *
   * @param <T> the entity type
   * @param type the entity class
   * @return the cached entity metadata
   */
  @SuppressWarnings("unchecked")
  public <T> NitriteEntityMeta<T> getOrBuildMeta(Class<T> type) {
    // Fast path: avoid computeIfAbsent lock contention on cache hits
    NitriteEntityMeta<T> existing = (NitriteEntityMeta<T>) entityMetaCache.get(type);
    if (existing != null) {
      return existing;
    }
    // Lambda allocation only happens on cache miss path now
    return (NitriteEntityMeta<T>) entityMetaCache.computeIfAbsent(
        type, k -> buildEntityMeta(runtimeEntityRegistry.getEntity((Class<T>) k)));
  }

  /**
   * Convert entity to Document, handling Geometry fields specially.
   * Geometry objects must be preserved as-is for Nitrite's spatial module.
   */
  @SuppressWarnings({"unchecked"})
  private <T> @Nullable Document convertToDocumentInternal(@Nullable T entity, Set<Object> visited) {
    if (entity == null) {
      return null;
    }

    NitriteEntityMeta<T> meta = getOrBuildMeta((Class<T>) entity.getClass());
    Document doc = Document.createDocument();

    for (WritablePropertyMeta<T> wpm : meta.writableProps()) {
      Object value = wpm.prop().getProperty().get(entity);
      if (value == null) {
        continue;
      }
      String fieldName = wpm.fieldName();
      PropertyStrategy strategy = wpm.strategy();
      if (strategy == null) {
        continue;
      }
      Object stored = switch (strategy) {
        case JAVA_PASSTHROUGH, GEOMETRY          -> value;
        case INSTANT, LOCAL_DATE, LOCAL_DATETIME,
             LOCAL_TIME, ZONED_DATE_TIME,
             OFFSET_DATE_TIME, UUID, URL,
             URI, CHARSET                        -> NitriteTypeRegistry.write(value);
        case ENUM                                -> ((Enum<?>) value).name();
        case OPTIONAL                            -> ((Optional<?>) value).map(this::toFilterValue).orElse(null);
        case ENTITY_ID_REF, ASSOCIATION_ID_REF   -> wpm.associatedIdProp() != null ? toFilterValue(wpm.associatedIdProp().get(value)) : null;
        case MAP, INTROSPECTED_POJO              -> toDocumentValue(value);
        case SERDE                               -> {
          if (serdeObjectMapper == null) {
            yield value;
          }
          try {
            String json = serdeObjectMapper.writeValueAsString(value);
            yield serdeObjectMapper.readValue(json, Object.class);
          } catch (Exception e) {
            yield value;
          }
        }
        case ASSOCIATION_EMBEDDED                -> convertAssociation(value, (RuntimeAssociation<T>) wpm.prop(), visited);
        case ASSOCIATION_IDS_REF                 -> {
          List<Object> ids = new ArrayList<>();
          for (Object item : (Iterable<?>) value) {
            if (item != null && wpm.associatedIdProp() != null) {
              Object idValue = wpm.associatedIdProp().get(item);
              if (idValue != null) {
                ids.add(toFilterValue(idValue));
              }
            }
          }
          yield ids.isEmpty() ? null : ids;
        }
        case ASSOCIATION_MAPPED_BY               -> null;
      };
      if (stored != null) {
        doc.put(fieldName, stored);
      }
      if (!wpm.compositeJoinColumns().isEmpty() && value != null) {
        BeanIntrospection<Object> associatedIntro = BeanIntrospector.SHARED.getIntrospection(castClass(value.getClass()));
        for (CompositeJoinColumn joinColumn : wpm.compositeJoinColumns()) {
          associatedIntro.getProperty(joinColumn.referencedProperty()).ifPresent(referencedProp -> {
            Object referencedValue = referencedProp.get(value);
            if (referencedValue != null) {
              doc.put(joinColumn.localName(), toFilterValue(referencedValue));
            }
          });
        }
      }
    }

    // Identity
    RuntimePersistentProperty<T> idProp = meta.idProp();
    if (idProp != null) {
      Object idValue = idProp.getProperty().get(entity);
      if (idValue != null) {
        doc.put(ID_FIELD, normalizeIdentityValue(idProp, idValue));
      }
    }

    // Version
    RuntimePersistentProperty<T> versionProp = meta.versionProp();
    if (versionProp != null) {
      Object versionValue = versionProp.getProperty().get(entity);
      if (versionValue != null) {
        doc.put(versionProp.getPersistedName(), toFilterValue(versionValue));
      }
    }

    return doc;
  }

  private <A> @Nullable Object convertAssociation(@Nullable Object value, RuntimeAssociation<A> association, Set<Object> visited) {
      if (value == null) {
          return null;
      }
      if (value instanceof Iterable<?> iterable) {
          List<Object> list = new ArrayList<>();
          for (Object item : iterable) {
              list.add(convertSingleAssociation(item, association, visited));
          }
          return list;
      }
      return convertSingleAssociation(value, association, visited);
  }

  private <A> @Nullable Object convertSingleAssociation(@Nullable Object value, RuntimeAssociation<A> association, Set<Object> visited) {
      if (value == null) {
          return null;
      }

      if (visited.contains(value)) {
          RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
          RuntimePersistentProperty<?> idProp = safeGetIdentity(associatedEntity);
          if (idProp != null) {
              @SuppressWarnings({"rawtypes", "unchecked"})
              Object idValue = ((BeanProperty) idProp.getProperty()).get(value);
              if (idValue != null) {
                  return toFilterValue(idValue);
              }
          }
          return null;
      }

      // Embed the full association if not visited
      return toDocumentInternal(value, visited);
  }

  /**
   * Converts a plain POJO with no registered {@link BeanIntrospection} (e.g. a class not
   * processed by the Micronaut annotation processor) to a map using standard JDK JavaBean
   * reflection ({@link Introspector}). This is the fallback used when a value is
   * neither a {@link Document}, a {@link Map}, nor an {@code @Introspected} type.
   *
   * @param value the POJO to convert
   * @return a map representation, keyed by JavaBean property name
   */
  private Map<String, Object> reflectToMap(Object value) {
    try {
      Map<String, Object> map = new LinkedHashMap<>();
      BeanInfo beanInfo = Introspector.getBeanInfo(value.getClass(), Object.class);
      for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
        Method readMethod = pd.getReadMethod();
        if (readMethod != null) {
          Object propertyValue = readMethod.invoke(value);
          // Document storage requires Serializable values; skip synthetic properties
          // (e.g. Groovy's getMetaClass()) that don't satisfy that constraint.
          if (propertyValue == null || propertyValue instanceof Serializable) {
            map.put(pd.getName(), propertyValue);
          }
        }
      }
      return map;
    } catch (Exception e) {
      throw new DataAccessException(
          "Could not convert value of type " + value.getClass() + " to a Document", e);
    }
  }

  /**
   * Recursively converts an {@code @Introspected} POJO to a plain {@link LinkedHashMap} for
   * Nitrite Document storage. Nested {@code @Introspected} types are converted recursively;
   * all other values are normalised via {@link #toFilterValue}.
   *
   * @param pojo the POJO to convert (must have a registered {@link BeanIntrospection})
   * @return a map representation suitable for Nitrite storage
   */
  private Map<String, Object> pojoToMap(Object pojo) {
    BeanIntrospection<Object> intro =
        BeanIntrospector.SHARED.getIntrospection(castClass(pojo.getClass()));
    Map<String, Object> map = new LinkedHashMap<>();
    for (BeanProperty<Object, Object> p : intro.getBeanProperties()) {
      if (p.isWriteOnly()) {
        continue;
      }
      Object v = p.get(pojo);
      if (v == null) {
        continue;
      }
      if (BeanIntrospector.SHARED.findIntrospection(v.getClass()).isPresent()) {
        map.put(p.getName(), pojoToMap(v));
      } else {
        map.put(p.getName(), toFilterValue(v));
      }
    }
    return map;
  }

  /**
   * Reconstructs an {@code @Introspected} POJO from a stored {@link Map}. Nested maps whose
   * target property type also has a {@link BeanIntrospection} are reconstructed recursively;
   * scalar values are coerced via {@link ConversionService}.
   *
   * @param map  the stored map (as returned by Nitrite on read)
   * @param intro the introspection for the target type
   * @param <P> the target POJO type
   * @return a populated instance of the target type
   */
  private <P> P mapToPojo(Map<?, ?> map, BeanIntrospection<P> intro) {
    Argument<?>[] ctorArgs = intro.getConstructorArguments();
    P pojo;
    Set<String> ctorArgNames = new HashSet<>();
    if (ctorArgs.length > 0) {
      Object[] args = new Object[ctorArgs.length];
      for (int i = 0; i < ctorArgs.length; i++) {
        Argument<?> arg = ctorArgs[i];
        String argName = arg.getName();
        ctorArgNames.add(argName);
        Object raw = getMapValueByName(map, argName);
        args[i] = raw == null ? null : convertFromDocumentValue(raw, arg);
      }
      pojo = intro.instantiate(args);
    } else {
      pojo = intro.instantiate();
    }
    for (BeanProperty<P, Object> p : intro.getBeanProperties()) {
      if (p.isReadOnly() || ctorArgNames.contains(p.getName())) {
        continue;
      }
      Object v = getMapValueByName(map, p.getName());
      if (v != null) {
        p.set(pojo, convertFromDocumentValue(v, p.asArgument()));
      }
    }
    return pojo;
  }

  private @Nullable Object getMapValueByName(Map<?, ?> map, String name) {
    Object value = map.get(name);
    if (value != null) {
      return value;
    }
    String snake = NameUtils.camelToSnake(name);
    if (!Objects.equals(name, snake)) {
      value = map.get(snake);
      if (value != null) {
        return value;
      }
    }
    String camel = NameUtils.snakeToCamel(name);
    if (!Objects.equals(name, camel)) {
      value = map.get(camel);
      return value;
    }
    return null;
  }

  /**
   * Check if a type is a simple type that should not be converted to a Document.
   *
   * @param type the type to check
   * @return true if the type is a simple type
   */
  public boolean isSimpleType(Class<?> type) {
    return type.isPrimitive() ||
           Number.class.isAssignableFrom(type) ||
           String.class.equals(type) ||
           Boolean.class.equals(type) ||
           Character.class.equals(type) ||
           Date.class.isAssignableFrom(type) ||
           Temporal.class.isAssignableFrom(type) ||
           UUID.class.equals(type) ||
           URL.class.equals(type) ||
           URI.class.equals(type) ||
           Charset.class.equals(type);
  }

  /**
   * Convert a value from a Document to the target argument type.
   *
   * @param value the value to convert
   * @param target the target argument type
   * @return the converted value
   */
  public @Nullable Object convertFromDocumentValue(@Nullable Object value, Argument<?> target) {
    if (value == null) {
      return null;
    }
    Class<?> t = target.getType();
    if (value instanceof Document document && !Document.class.isAssignableFrom(t)) {
      value = documentToMap(document);
    }
    if ((value instanceof Number || value instanceof String) && NitriteTypeRegistry.hasEntry(t)) {
      return valueConverter.convertWithTemporalHandling(value, t);
    }

    if (value instanceof Map<?, ?> mapValue) {
      if (Map.class.isAssignableFrom(t)) {
        Argument<?>[] typeParameters = target.getTypeParameters();
        if (typeParameters.length == 2) {
          Argument<?> valueArg = typeParameters[1];
          Map<Object, Object> result = new LinkedHashMap<>();
          for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
            result.put(entry.getKey(), convertFromDocumentValue(entry.getValue(), valueArg));
          }
          return result;
        }
      } else if (!t.isInstance(value)) {
        // Map → POJO: prefer BeanIntrospection (no Serde codec required) when available,
        // fall back to Serde for types with custom codecs but no @Introspected metadata.
        Optional<BeanIntrospection<Object>> maybeIntro =
            BeanIntrospector.SHARED.findIntrospection(castClass(t));
        if (maybeIntro.isPresent()) {
          return mapToPojo(mapValue, maybeIntro.get());
        }
        if (serdeObjectMapper != null) {
          try {
            String json = serdeObjectMapper.writeValueAsString(value);
            return serdeObjectMapper.readValue(json, target);
          } catch (Exception e) {
            return conversionService.convert(value, target).orElse(null);
          }
        }
      }
    }

    if (value instanceof Iterable<?> iterable && Iterable.class.isAssignableFrom(t)) {
      Argument<?>[] typeParameters = target.getTypeParameters();
      if (typeParameters.length == 1) {
        Argument<?> itemArg = typeParameters[0];
        List<Object> result = new ArrayList<>();
        for (Object item : iterable) {
          result.add(convertFromDocumentValue(item, itemArg));
        }
        if (Set.class.isAssignableFrom(t)) {
          return new LinkedHashSet<>(result);
        }
        return result;
      }
    }

    if (t.isInstance(value)) {
      return value;
    }

    return conversionService.convert(value, target).orElse(null);
  }

  private Map<String, Object> documentToMap(Document document) {
    return documentFieldsToMap(document, "", document.getFields());
  }

  private Map<String, Object> documentFieldsToMap(Document document, String prefix, Set<String> fields) {
    Map<String, Object> map = new LinkedHashMap<>();
    Set<String> nestedRoots = new LinkedHashSet<>();
    for (String field : fields) {
      if (!field.startsWith(prefix)) {
        continue;
      }
      String relativeField = field.substring(prefix.length());
      int separator = relativeField.indexOf('.');
      if (separator < 0) {
        map.put(relativeField, document.get(field));
      } else {
        nestedRoots.add(relativeField.substring(0, separator));
      }
    }
    for (String nestedRoot : nestedRoots) {
      map.put(nestedRoot, documentFieldsToMap(document, prefix + nestedRoot + ".", fields));
    }
    return map;
  }

  /**
   * Serialize a scalar field value to a JSON-compatible type for Nitrite Document storage.
   * Uses Serde so that custom Jackson/Serde annotations on the value type are respected.
   * Falls back to {@link #toFilterValue} for Serde-incompatible types.
   */
  private @Nullable Object serializeForDocument(@Nullable Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return value;
    }
    // toFilterValue handles common types cheaply (Instant → String, UUID → String, Enum → name, etc.).
    // If it converts the value (identity check), use that result — no Serde overhead on the hot path.
    Object filtered = toFilterValue(value);
    if (!Objects.equals(filtered, value)) {
      return filtered;
    }
    // toFilterValue returned the original: either a java.* type (collection, array — store as-is)
    // or a custom POJO it doesn't know about. For custom non-java types, use Serde so that
    // Jackson/Serde annotations (@JsonSerialize etc.) on the type are respected.
    if (serdeObjectMapper != null && !value.getClass().getName().startsWith("java.") && !value.getClass().isArray()) {
      try {
        String json = serdeObjectMapper.writeValueAsString(value);
        return serdeObjectMapper.readValue(json, Object.class);
      } catch (Exception e) {
        return value;
      }
    }
    return filtered;
  }

  private @Nullable Object normalizeIdentityValue(RuntimePersistentProperty<?> idProperty, @Nullable Object idValue) {
    if (idValue == null) {
      return null;
    }
    if (idProperty.isAnnotationPresent(EmbeddedId.class)) {
      return toDocumentValue(idValue);
    }
    Class<?> idType = idProperty.getType();
    if (isSimpleType(idType) || idType.isEnum() || idType.isArray() || idType.getName().startsWith("java.")) {
      return toFilterValue(idValue);
    }
    return toDocumentValue(idValue);
  }

  private @Nullable Document toDocumentValue(@Nullable Object value) {
    return switch (value) {
      case null -> null;
      case Document document -> document;
      case Map<?, ?> map -> {
        Document document = Document.createDocument();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          Object nested = entry.getValue();
          Object serialized = serializeForDocument(nested);
          if (serialized instanceof Map<?, ?> nestedMap && !(serialized instanceof Document)) {
            document.put(String.valueOf(entry.getKey()), toDocumentValue(nestedMap));
          } else {
            document.put(String.valueOf(entry.getKey()), serialized);
          }
        }
        yield document;
      }
      default -> {
        Optional<BeanIntrospection<Object>> maybeIntro = BeanIntrospector.SHARED.findIntrospection(castClass(value.getClass()));
        if (maybeIntro.isPresent()) {
          Document document = Document.createDocument();
          for (Map.Entry<String, Object> entry : pojoToMap(value).entrySet()) {
            Object nested = entry.getValue();
            Object serialized = serializeForDocument(nested);
            if (serialized instanceof Map<?, ?> nestedMap && !(serialized instanceof Document)) {
              document.put(entry.getKey(), toDocumentValue(nestedMap));
            } else {
              document.put(entry.getKey(), serialized);
            }
          }
          yield document;
        }
        yield toDocumentValue(reflectToMap(value));
      }
    };
  }

  private @Nullable Document toPersistedDocument(@Nullable Document document) {
    if (document == null) {
      return null;
    }
    Document persisted = Document.createDocument();
    for (String field : document.getFields()) {
      Object value = document.get(field);
      if (value instanceof Document nested) {
        persisted.put(NameUtils.camelToSnake(field), toPersistedDocument(nested));
      } else {
        persisted.put(NameUtils.camelToSnake(field), value);
      }
    }
    return persisted;
  }

  /**
   * Hydrate an entity from a Nitrite Document.
   *
   * <p>Non-embedded single-valued associations stored as foreign keys are eagerly resolved while
   * the document is mapped, including when the repository query does not declare {@code @Join}.
   * Each root document has an independent hydration cache, so loading multiple roots can issue one
   * additional collection lookup per to-one association per root and can recursively hydrate the
   * referenced graph. Explicit join fetching is currently batched only for inverse
   * {@code ONE_TO_MANY}/{@code MANY_TO_MANY} associations.
   *
   * @param doc the Nitrite document
   * @param type the entity type
   * @param <T> the entity type
   * @return the hydrated entity
   */
  public <T> @Nullable T fromDocument(final @Nullable Document doc, final Class<T> type) {
    return fromDocumentInternal(doc, type, new HashMap<>());
  }

  @SuppressWarnings("unchecked")
  private <T> @Nullable T fromDocumentInternal(final @Nullable Document doc, final Class<T> type, final Map<String, Object> visited) {
    if (doc == null) {
        return null;
    }

    // Check if we've already hydrated this document to avoid infinite recursion
    Object id = doc.get(ID_FIELD);
    if (id == null) {
        id = doc.get("_id");
    }
    String cacheKey = null;
    if (id != null) {
        cacheKey = type.getName() + ":" + id;
        if (visited.containsKey(cacheKey)) {
            return (T) visited.get(cacheKey);
        }
    }

    if (LOG.isDebugEnabled()) {
        LOG.debug("fromDocumentInternal: type={}, doc={}", type.getName(), doc);
    }
    RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
    BeanIntrospection<T> introspection = persistentEntity.getIntrospection();

    RuntimePersistentProperty<T> idProp = safeGetIdentity(persistentEntity);

    T entity;
    Argument<?>[] ctorArgs = introspection.getConstructorArguments();
    if (ctorArgs.length > 0) {
        Object[] args = new Object[ctorArgs.length];
        for (int i = 0; i < ctorArgs.length; i++) {
            Argument<?> arg = ctorArgs[i];
            String name = arg.getName();
            RuntimePersistentProperty<T> prop = persistentEntity.getPropertyByName(name);
            String storedName = prop != null ? prop.getPersistedName() : name;
            if (idProp != null && idProp.getName().equals(name)) {
                storedName = ID_FIELD;
            }
            Object val = storedName.equals(ID_FIELD)
                ? docGet(doc, storedName, name, "_id")
                : docGet(doc, storedName, name);
            if (val == null) {
                args[i] = null;
            } else if (prop instanceof RuntimeAssociation && (prop.isReadOnly() || arg.isNonNull())) {
                // The association has to be supplied at construction time: either there is no
                // writable property to populate afterwards, or the constructor parameter rejects
                // null (a Kotlin non-nullable parameter).
                args[i] = resolveAssociationValue(prop, val, visited);
            } else if (prop instanceof RuntimeAssociation) {
                // Writable associations are populated after instantiation. Deferring them lets the
                // current entity enter the visited cache before a bidirectional association is
                // hydrated and avoids resolving the same association twice.
                args[i] = null;
            } else {
                args[i] = convertFromDocumentValue(val, arg);
            }
        }
        entity = introspection.instantiate(false, args);
    } else {
        entity = introspection.instantiate();
    }

    if (cacheKey != null) {
        // Cache before populating properties so that a back-reference resolves to this instance.
        visited.put(cacheKey, entity);
    }

    // Set identity early so mappedBy hydration can use it
    if (idProp != null) {
        Object storedId = docGet(doc, ID_FIELD, idProp.getPersistedName(), idProp.getName(), "_id");
        if (storedId != null && !(storedId instanceof NitriteId)) {
            Object convertedId = convertFromDocumentValue(storedId, idProp.getProperty().asArgument());
            if (convertedId != null) {
                idProp.getProperty().set(entity, convertedId);
            }
        }
    }

    // A composite identity has no single id property and its properties are not part of
    // getPersistentProperties(), so they are read back explicitly, mirroring how they are written.
    if (idProp == null && persistentEntity.hasCompositeIdentity()) {
        for (RuntimePersistentProperty<T> identityProperty : persistentEntity.getRuntimeIdentityProperties()) {
            if (identityProperty.isReadOnly()) {
                continue;
            }
            Object storedValue = docGet(doc, identityProperty.getPersistedName(), identityProperty.getName());
            if (storedValue != null) {
                Object convertedValue = convertFromDocumentValue(storedValue, identityProperty.getProperty().asArgument());
                if (convertedValue != null) {
                    identityProperty.getProperty().set(entity, convertedValue);
                }
            }
        }
    }

    // Populate properties
    for (RuntimePersistentProperty<T> prop : persistentEntity.getPersistentProperties()) {
        if (prop.isReadOnly() || prop.isAnnotationPresent(Transient.class)) {
            continue;
        }
        String storedName = prop.getPersistedName();
        if (idProp != null && idProp.equals(prop)) {
            storedName = ID_FIELD;
        }

        BeanProperty<T, Object> property = prop.getProperty();
        Object value = storedName.equals(ID_FIELD)
            ? docGet(doc, storedName, prop.getName(), "_id")
            : docGet(doc, storedName, prop.getName());

        if (value == null && prop instanceof RuntimeAssociation<?>) {
            // An association to a composite-identity entity stores no single id reference, only the
            // local fields named by its @JoinColumns, so it is resolved from those instead.
            Object byJoinColumns = resolveByCompositeJoinColumns(doc, prop, type, visited);
            if (byJoinColumns != null) {
                property.set(entity, byJoinColumns);
                continue;
            }
        }

        if (value != null) {
            boolean associationStoredEmbedded = isAssociationStoredEmbedded(prop);
            if (prop instanceof RuntimeAssociation<?> association && !associationStoredEmbedded) {
                Class<Object> associatedType = castClass(association.getAssociatedEntity().getIntrospection().getBeanType());
                // A @Join fetch replaces the raw foreign-key scalar with the full joined
                // sub-document (or array of them) via $lookup — hydrate directly from what's
                // already there rather than treating it as an id to re-query.
                if (value instanceof Document embeddedDoc) {
                    property.set(entity, fromDocumentInternal(embeddedDoc, associatedType, visited));
                    continue;
                } else if (value instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Document) {
                    List<Object> associatedEntities = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Document d) {
                            associatedEntities.add(fromDocumentInternal(d, associatedType, visited));
                        }
                    }
                    property.set(entity, conversionService.convert(associatedEntities, property.asArgument()).orElse(null));
                    continue;
                } else if (helper != null) {
                    if (value instanceof Iterable<?> ids) {
                        List<Object> associatedEntities = new ArrayList<>();
                        for (Object associatedId : ids) {
                            Document associatedDoc = helper.getCollection(associatedType).find(idEqualsFilter(associatedType, associatedId)).firstOrNull();
                            if (associatedDoc != null) {
                                associatedEntities.add(fromDocumentInternal(associatedDoc, associatedType, visited));
                            }
                        }
                        property.set(entity, conversionService.convert(associatedEntities, property.asArgument()).orElse(null));
                        continue;
                    } else {
                        // It's a single ID
                        Document associatedDoc = helper.getCollection(associatedType).find(idEqualsFilter(associatedType, value)).firstOrNull();
                        if (associatedDoc != null) {
                            property.set(entity, fromDocumentInternal(associatedDoc, associatedType, visited));
                            continue;
                        }
                    }
                }
            } else if (prop instanceof RuntimeAssociation<?> association) {
                if (value instanceof Document embeddedDoc) {
                    property.set(entity, fromDocumentInternal(embeddedDoc, castClass(association.getAssociatedEntity().getIntrospection().getBeanType()), visited));
                    continue;
                } else if (value instanceof List<?> list) {
                    List<Object> embeddedEntities = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Document d) {
                            embeddedEntities.add(fromDocumentInternal(d, castClass(association.getAssociatedEntity().getIntrospection().getBeanType()), visited));
                        }
                    }
                    property.set(entity, conversionService.convert(embeddedEntities, property.asArgument()).orElse(null));
                    continue;
                }
            }

            Object converted = convertFromDocumentValue(value, property.asArgument());
            if (converted != null) {
                property.set(entity, converted);
            }
        }
        // Note: mappedBy associations (ONE_TO_MANY, MANY_TO_MANY) are NOT auto-hydrated.
        // They remain lazy by default. @Join support would require explicit fetch logic.
    }

    // Handle version property explicitly (might not be in persistent properties or value could be 0)
    RuntimePersistentProperty<T> versionProp = getOrBuildMeta(type).versionProp();
    if (versionProp != null && !versionProp.isReadOnly()) {
        BeanProperty<T, Object> versionProperty = versionProp.getProperty();
        Object versionValue = docGet(doc, versionProp.getPersistedName(), versionProp.getName());
        if (versionValue != null) {
            Object convertedVersion = convertFromDocumentValue(versionValue, versionProperty.asArgument());
            if (convertedVersion != null) {
                versionProperty.set(entity, convertedVersion);
            }
        }
    }

    // Trigger postLoad event
    runtimeEntityRegistry.getEntityEventListener().postLoad(
        (EntityEventContext<Object>) new DefaultEntityEventContext<>(persistentEntity, entity)
    );

    return entity;
  }

    /**
     * Resolve the raw foreign-key value of an association-typed constructor argument into the actual
     * associated entity. Constructor arguments would otherwise be passed straight to
     * {@link #convertFromDocumentValue}, which cannot turn the foreign key into the associated entity
     * type, yielding {@code null} and (for a non-nullable constructor parameter) an instantiation
     * failure.
     *
     * <p>Only read-only, single-valued {@code MANY_TO_ONE}/{@code ONE_TO_ONE} constructor
     * associations reach this path. Writable constructor associations are populated after the
     * current entity is instantiated and entered into the hydration cache.
     * {@link #fromDocumentInternal} returns {@code null} for a missing (dangling) reference.
     *
     * @param prop      the association property being resolved (guaranteed a {@link RuntimeAssociation} by the caller)
     * @param rawValue  the raw foreign-key value read from the document (never null)
     * @param visited   the in-progress hydration cache, to short-circuit cycles
     * @return the hydrated associated entity, or {@code null} if the reference is dangling
     */
    private <T> @Nullable Object resolveAssociationValue(RuntimePersistentProperty<T> prop, Object rawValue, Map<String, Object> visited) {
        if (helper == null) {
            return null;
        }
        RuntimeAssociation<?> association = (RuntimeAssociation<?>) prop;
        Class<Object> associatedType = castClass(association.getAssociatedEntity().getIntrospection().getBeanType());
        if (rawValue instanceof Document joinedDoc) {
            // A @Join fetch already replaced the foreign key with the joined sub-document.
            return fromDocumentInternal(joinedDoc, associatedType, visited);
        }
        Document associatedDoc = helper.getCollection(associatedType).find(idEqualsFilter(associatedType, rawValue)).firstOrNull();
        return fromDocumentInternal(associatedDoc, associatedType, visited);
    }

    /**
     * Resolves an association that stores no single id reference, using the local fields named by
     * its {@code @JoinColumn} mapping. This is the read-side counterpart of the composite join
     * column write in {@code toDocumentInternal}, and is what lets an association to an entity with
     * a composite identity hydrate at all: such an entity has no single id property, so no id
     * reference field is ever written for it.
     *
     * @param doc     the source document
     * @param prop    the association property being resolved
     * @param type    the entity type being hydrated, used to look up its pre-computed metadata
     * @param visited the in-progress hydration cache, to short-circuit cycles
     * @param <T>     the entity type being hydrated
     * @return the hydrated associated entity, or {@code null} if the association declares no
     *     composite join columns, any of them is absent from the document, or nothing matches
     */
    private <T> @Nullable Object resolveByCompositeJoinColumns(Document doc,
                                                              RuntimePersistentProperty<T> prop,
                                                              Class<T> type,
                                                              Map<String, Object> visited) {
        if (helper == null) {
            return null;
        }
        List<CompositeJoinColumn> joinColumns = List.of();
        for (WritablePropertyMeta<T> wpm : getOrBuildMeta(type).writableProps()) {
            if (wpm.prop().getName().equals(prop.getName())) {
                joinColumns = wpm.compositeJoinColumns();
                break;
            }
        }
        if (joinColumns.isEmpty()) {
            return null;
        }
        RuntimePersistentEntity<?> associatedEntity = ((RuntimeAssociation<?>) prop).getAssociatedEntity();
        List<Filter> filters = new ArrayList<>(joinColumns.size());
        for (CompositeJoinColumn joinColumn : joinColumns) {
            Object localValue = doc.get(joinColumn.localName());
            if (localValue == null) {
                return null;
            }
            RuntimePersistentProperty<?> referenced = associatedEntity.getPropertyByName(joinColumn.referencedProperty());
            String referencedField = referenced != null ? referenced.getPersistedName() : joinColumn.referencedProperty();
            filters.add(NitriteFilterUtils.eq(referencedField, localValue));
        }
        Class<Object> associatedType = castClass(associatedEntity.getIntrospection().getBeanType());
        Filter filter = filters.size() == 1 ? filters.get(0) : Filter.and(filters.toArray(new Filter[0]));
        Document associatedDoc = helper.getCollection(associatedType).find(filter).firstOrNull();
        return associatedDoc == null ? null : fromDocumentInternal(associatedDoc, associatedType, visited);
    }

    private static <T> boolean isAssociationStoredEmbedded(RuntimePersistentProperty<T> prop) {
        boolean associationStoredEmbedded;
        if (prop instanceof RuntimeAssociation<T> association && !association.isEmbedded()) {
            // Mirrors the write-side rule in getOrBuildMeta: an associated entity with a composite
            // identity has no single id property either, but it is still an id reference and was
            // not written embedded, so it must not be read back as one.
            associationStoredEmbedded = safeGetIdentity(association.getAssociatedEntity()) == null
                && !association.getAssociatedEntity().hasCompositeIdentity();
        } else {
            associationStoredEmbedded = prop instanceof RuntimeAssociation<T> association && association.isEmbedded();
        }
        return associationStoredEmbedded;
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> castClass(Class<?> clazz) {
        return (Class<T>) clazz;
    }

    private static @Nullable Object docGet(Document doc, String... keys) {
        for (String key : keys) {
            Object val = doc.get(key);
            if (val != null) {
                return val;
            }
        }
        return null;
    }
}
