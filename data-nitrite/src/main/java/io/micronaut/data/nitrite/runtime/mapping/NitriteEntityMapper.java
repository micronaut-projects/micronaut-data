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
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.NameUtils;
import io.micronaut.data.nitrite.runtime.NitriteOperationsHelper;
import io.micronaut.data.nitrite.runtime.ValueConverter;
import io.micronaut.serde.ObjectMapper;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteId;
import org.dizitart.no2.common.mapper.NitriteMapper;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
 * @since 1.0.0
 */
@Internal
public final class NitriteEntityMapper {

  private static final Logger LOG = LoggerFactory.getLogger(NitriteEntityMapper.class);
  private static final String ID_FIELD = "id";
  private static final String GEOMETRY_CLASS = "org.locationtech.jts.geom.Geometry";

  private final ConversionService conversionService;
  private final ValueConverter valueConverter;
  private final ObjectMapper serdeObjectMapper;
  private final NitriteMapper nitriteMapper;
  private final RuntimeEntityRegistry runtimeEntityRegistry;
  private NitriteOperationsHelper helper;
  private final Class<?> geometryClass;
  private final ConcurrentHashMap<Class<?>, NitriteEntityMeta<?>> entityMetaCache = new ConcurrentHashMap<>();

  /**
   * Create a new mapper.
   *
   * <p><strong>Architecture:</strong> This mapper uses Micronaut Serde at the boundary
   * for entity ↔ Map conversion, and ConversionService for individual field conversions.
   * Jackson is used only internally by Nitrite for Document storage.</p>
   *
   * @param conversionService the conversion service (for field-level conversions)
   * @param serdeObjectMapper the Micronaut Serde ObjectMapper (for entity ↔ Map conversion at boundary)
   * @param nitriteMapper the Nitrite mapper
   * @param runtimeEntityRegistry the runtime entity registry
   */
  public NitriteEntityMapper(
      final ConversionService conversionService,
      final ObjectMapper serdeObjectMapper,
      final NitriteMapper nitriteMapper,
      final RuntimeEntityRegistry runtimeEntityRegistry) {
    this.conversionService = conversionService;
    this.valueConverter = new ValueConverter(conversionService);
    this.serdeObjectMapper = serdeObjectMapper;
    this.nitriteMapper = nitriteMapper;
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
  public Object toFilterValue(Object value) {
    Object result = ValueConverter.toFilterValueStatic(value);
    if (result != value) {
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
            RuntimePersistentProperty<Object> idProp = entity.getIdentity();
            Object idValue = idProp.getProperty().get(value);
            if (idValue != null) {
                return toFilterValue(idValue);
            }
        }
    } catch (Exception ignored) {
    }

    return value;
  }

  /**
   * Convert a value to a format suitable for Nitrite Filters, considering property metadata.
   *
   * @param val the raw value
   * @return the normalized value
   */
  public Object toNitriteFilterValue(final Object val) {
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
          RuntimePersistentProperty<Object> idProp = entity.getIdentity();
          Object idValue = idProp.getProperty().get(val);
          if (idValue != null) {
            return normalizeIdentityValue(idProp, idValue);
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
  public <T> Object getEntityIdValue(final T entity, final Class<T> type) {
    RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
    RuntimePersistentProperty<T> idProp = safeGetIdentity(persistentEntity);
    if (idProp != null) {
      return idProp.getProperty().get(entity);
    }
    return null;
  }

  private <E> RuntimePersistentProperty<E> safeGetIdentity(RuntimePersistentEntity<E> persistentEntity) {
    try {
      return persistentEntity.getIdentity();
    } catch (IllegalStateException e) {
      return null;
    }
  }

  /**
   * Normalize a field name.
   *
   * @param field the field name
   * @return the normalized field name
   */
  public String normalizeFieldName(final String field) {
      return normalizeFieldName(field, null);
  }

  /**
   * Normalize a field name.
   *
   * @param field the field name
   * @param entity the entity metadata
   * @return the normalized field name
   */
  public String normalizeFieldName(final String field, @Nullable final RuntimePersistentEntity<?> entity) {
    if (entity != null) {
      RuntimePersistentProperty<?> idProperty = safeGetIdentity(entity);
      if (idProperty != null && (idProperty.getName().equals(field) || "_id".equals(field) || "id".equals(field))) {
        return ID_FIELD;
      }
      RuntimePersistentProperty<?> prop = entity.getPropertyByName(field);
      if (prop != null) {
        return prop.getPersistedName();
      }
    }
    return "_id".equals(field) ? ID_FIELD : field;
  }

  /**
   * Create a filter matching the entity ID.
   *
   * @param type the entity class
   * @param id the ID value
   * @return the Nitrite Filter
   * @param <T> the entity type
   */
  public <T> Filter idEqualsFilter(final Class<T> type, final Object id) {
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
  public <T> Filter idEqualsFilter(final NitriteEntityMeta<T> meta, final Object id) {
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
   * @return the Nitrite Filter
   */
  public <E> Filter eqWithNumericCoercion(final RuntimePersistentEntity<E> entity, final String field, final Object value, final String dottedPath) {
    if (LOG.isDebugEnabled()) {
        LOG.debug("eqWithNumericCoercion: field={}, value={}, type={}, dottedPath={}", field, value, (value != null ? value.getClass().getName() : "null"), dottedPath);
    }

    Filter base = FluentFilter.where(dottedPath).eq(value);
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
            return FluentFilter.where(dottedPath).eq(converted.get());
          }
        }
        Filter precise = switch (targetType) {
          case Class<?> t when t == int.class   || t == Integer.class -> FluentFilter.where(dottedPath).eq(n.intValue());
          case Class<?> t when t == long.class  || t == Long.class    -> FluentFilter.where(dottedPath).eq(n.longValue());
          case Class<?> t when t == double.class || t == Double.class -> FluentFilter.where(dottedPath).eq(n.doubleValue());
          case Class<?> t when t == float.class || t == Float.class   -> FluentFilter.where(dottedPath).eq(n.floatValue());
          case Class<?> t when t == short.class || t == Short.class   -> FluentFilter.where(dottedPath).eq(n.shortValue());
          case Class<?> t when t == byte.class  || t == Byte.class    -> FluentFilter.where(dottedPath).eq(n.byteValue());
          default -> null;
        };
        if (precise != null) {
          return precise;
        }
      }
    }

    return switch (n) {
      case Integer i -> base;
      case Double d  -> base;
      case Float f   -> base;
      default        -> Filter.or(base,
          FluentFilter.where(dottedPath).eq(n.longValue()),
          FluentFilter.where(dottedPath).eq(n.intValue()),
          FluentFilter.where(dottedPath).eq(n.doubleValue()));
    };
  }

  /**
   * Convert an entity to a Nitrite Document.
   *
   * @param entity the entity instance
   * @return the Nitrite Document
   * @param <T> the entity type
   */
  public <T> Document toDocument(final T entity) {
    if (entity == null) {
      return null;
    }
    return toDocumentInternal(entity, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private <T> Document toDocumentInternal(final T entity, final Set<Object> visited) {
    if (entity == null) {
        return null;
    }
    if (visited.contains(entity)) {
      // If circular or null, return just the ID if possible
      return getEntityIdAsDocument(entity);
    }
    visited.add(entity);

    Document doc = convertToDocumentInternal(entity, visited);

    // Cache getEntity() result - called twice in original code, now only once
    RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(castClass(entity.getClass()));

    // Entities with @JsonProperty("_id") cause Jackson to serialize the id as "_id".
    // Nitrite reserves "_id" for NitriteId — rename user's id to its property name to avoid InvalidIdException.
    Object reservedId = doc.get("_id");
    if (reservedId != null && !(reservedId instanceof NitriteId)) {
      doc.remove("_id");
      RuntimePersistentProperty<T> idProp = safeGetIdentity(persistentEntity);
      String idField = idProp != null ? idProp.getName() : "id";
      doc.put(idField, toFilterValue(reservedId));
    }

    RuntimePersistentProperty<T> idProperty = safeGetIdentity(persistentEntity);
    Object normalizedId = null;
    Object idValue = idProperty != null ? idProperty.getProperty().get(entity) : null;
    boolean embeddedIdProperty = idProperty != null && idProperty.isAnnotationPresent(EmbeddedId.class);
    if (idValue != null && !embeddedIdProperty) {
      normalizedId = normalizeIdentityValue(idProperty, idValue);
      doc.put(ID_FIELD, normalizedId);
    }
    if (embeddedIdProperty && idValue != null) {
      try {
        Document idDoc = toDocumentValue(idValue);
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
      } catch (Exception ignored) {
      }
    } else if (normalizedId instanceof Document idDoc) {
      for (String field : idDoc.getFields()) {
        doc.put(field, toFilterValue(idDoc.get(field)));
      }
    }
    return doc;
  }

  private Document getEntityIdAsDocument(Object entity) {
      if (entity == null) {
          return null;
      }
      RuntimePersistentEntity<Object> persistentEntity = runtimeEntityRegistry.getEntity(castClass(entity.getClass()));
      RuntimePersistentProperty<Object> idProp = safeGetIdentity(persistentEntity);
      if (idProp != null) {
          Object idValue = idProp.getProperty().get(entity);
          if (idValue != null) {
              Document idDoc = Document.createDocument();
              idDoc.put(ID_FIELD, normalizeIdentityValue(idProp, idValue));
              return idDoc;
          }
      }
      return null;
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
      if (prop.isReadOnly()
          || prop.isAnnotationPresent(io.micronaut.data.annotation.Transient.class)
          || prop.getProperty().isAnnotationPresent(io.micronaut.data.annotation.Transient.class)) {
        continue;
      }

      String fieldName = prop.getPersistedName();
      PropertyStrategy strategy;
      String mappedByValue = null;
      BeanProperty<Object, Object> associatedIdProp = null;
      BeanProperty<Object, Object> backRefProp = null;

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
          RuntimePersistentProperty<?> idPropRaw;
          try {
            idPropRaw = assoc.getAssociatedEntity().getIdentity();
          } catch (IllegalStateException e) {
            idPropRaw = null;
          }
          if (idPropRaw == null) {
            strategy = PropertyStrategy.ASSOCIATION_EMBEDDED;
          } else {
            boolean isCollection = Iterable.class.isAssignableFrom(prop.getType());
            strategy = isCollection ? PropertyStrategy.ASSOCIATION_IDS_REF : PropertyStrategy.ASSOCIATION_ID_REF;
            associatedIdProp = (BeanProperty<Object, Object>) idPropRaw.getProperty();
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
                refEntity.getIdentity();
                associatedIdProp = refEntity.getIdentity().getProperty();
            }
          } catch (Exception ignored) {
          }
        }
      }

      WritablePropertyMeta<T> meta = new WritablePropertyMeta<>(
          prop, fieldName, strategy, mappedByValue, associatedIdProp, backRefProp);
      if (strategy == PropertyStrategy.ASSOCIATION_MAPPED_BY) {
        mappedByList.add(meta);
      } else {
        writableList.add(meta);
      }
    }

    // Cache ID accessor for fast ID property access
    BeanProperty<T, Object> idAccessor = null;
    RuntimePersistentProperty<T> idProp = null;
    try {
      idProp = persistentEntity.getIdentity();
    } catch (IllegalStateException e) {
      // entity has no identity (e.g. embedded) – leave null
    }
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
  private <T> Document convertToDocumentInternal(T entity, Set<Object> visited) {
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
      Object stored = switch (wpm.strategy()) {
        case JAVA_PASSTHROUGH, GEOMETRY          -> value;
        case INSTANT, LOCAL_DATE, LOCAL_DATETIME,
             LOCAL_TIME, ZONED_DATE_TIME,
             OFFSET_DATE_TIME, UUID, URL,
             URI, CHARSET                        -> NitriteTypeRegistry.write(value);
        case ENUM                                -> ((Enum<?>) value).name();
        case OPTIONAL                            -> ((Optional<?>) value).map(this::toFilterValue).orElse(null);
        case ENTITY_ID_REF, ASSOCIATION_ID_REF   -> wpm.associatedIdProp() != null ? toFilterValue(wpm.associatedIdProp().get(value)) : null;
        case INTROSPECTED_POJO                   -> pojoToMap(value);
        case SERDE                               -> {
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

  private <A> Object convertAssociation(Object value, RuntimeAssociation<A> association, Set<Object> visited) {
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

  private <A> Object convertSingleAssociation(Object value, RuntimeAssociation<A> association, Set<Object> visited) {
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
    Set<String> ctorArgNames = new java.util.HashSet<>();
    if (ctorArgs.length > 0) {
      Object[] args = new Object[ctorArgs.length];
      for (int i = 0; i < ctorArgs.length; i++) {
        Argument<?> arg = ctorArgs[i];
        String argName = arg.getName();
        ctorArgNames.add(argName);
        Object raw = getMapValueByName(map, argName);
        args[i] = raw == null ? null : convertMapValue(raw, arg);
      }
      pojo = intro.instantiate(args);
    } else {
      pojo = intro.instantiate();
    }
    for (BeanProperty<P, Object> p : intro.getBeanProperties()) {
      if (p.isReadOnly()) {
        continue;
      }
      if (ctorArgNames.contains(p.getName())) {
        continue;
      }
      Object v = getMapValueByName(map, p.getName());
      if (v == null) {
        continue;
      }
      p.set(pojo, convertMapValue(v, p.asArgument()));
    }
    return pojo;
  }

  private <T> Object convertMapValue(Object value, Argument<T> target) {
    if (value instanceof Map<?, ?> nested) {
      Optional<BeanIntrospection<T>> nestedIntro =
          BeanIntrospector.SHARED.findIntrospection(target.getType());
      if (nestedIntro.isPresent()) {
        return mapToPojo(nested, nestedIntro.get());
      }
    }
    return conversionService.convert(value, target).orElse(null);
  }

  private Object getMapValueByName(Map<?, ?> map, String name) {
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
   * Check if an object is a JTS Geometry. Uses the class reference cached at construction time.
   */
  private boolean isGeometry(Object value) {
    return geometryClass != null && geometryClass.isInstance(value);
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
           java.util.Date.class.isAssignableFrom(type) ||
           java.time.temporal.Temporal.class.isAssignableFrom(type) ||
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
  public Object convertFromDocumentValue(Object value, Argument<?> target) {
    if (value == null) {
      return null;
    }
    if (target.getType().isInstance(value)) {
      return value;
    }
    Class<?> t = target.getType();
    if ((value instanceof Number || value instanceof String) && NitriteTypeRegistry.hasEntry(t)) {
      return valueConverter.convertWithTemporalHandling(value, t);
    }
    // Map → POJO: prefer BeanIntrospection (no Serde codec required) when available,
    // fall back to Serde for types with custom codecs but no @Introspected metadata.
    if (value instanceof Map<?, ?> mapValue && !Map.class.isAssignableFrom(target.getType())) {
      Optional<BeanIntrospection<Object>> maybeIntro =
          BeanIntrospector.SHARED.findIntrospection(castClass(target.getType()));
      if (maybeIntro.isPresent()) {
        return mapToPojo(mapValue, maybeIntro.get());
      }
      try {
        String json = serdeObjectMapper.writeValueAsString(value);
        return serdeObjectMapper.readValue(json, target);
      } catch (Exception e) {
        return conversionService.convert(value, target).orElse(null);
      }
    }
    return conversionService.convert(value, target).orElse(null);
  }

  /**
   * Serialize a scalar field value to a JSON-compatible type for Nitrite Document storage.
   * Uses Serde so that custom Jackson/Serde annotations on the value type are respected.
   * Falls back to {@link #toFilterValue} for Serde-incompatible types.
   */
  private Object serializeForDocument(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return value;
    }
    // toFilterValue handles common types cheaply (Instant → String, UUID → String, Enum → name, etc.).
    // If it converts the value (identity check), use that result — no Serde overhead on the hot path.
    Object filtered = toFilterValue(value);
    if (filtered != value) {
      return filtered;
    }
    // toFilterValue returned the original: either a java.* type (collection, array — store as-is)
    // or a custom POJO it doesn't know about. For custom non-java types, use Serde so that
    // Jackson/Serde annotations (@JsonSerialize etc.) on the type are respected.
    if (!value.getClass().getName().startsWith("java.") && !value.getClass().isArray()) {
      try {
        String json = serdeObjectMapper.writeValueAsString(value);
        return serdeObjectMapper.readValue(json, Object.class);
      } catch (Exception e) {
        return value;
      }
    }
    return filtered;
  }

  private Object normalizeIdentityValue(RuntimePersistentProperty<?> idProperty, Object idValue) {
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

  private Document toDocumentValue(Object value) {
    return switch (value) {
      case null -> null;
      case Document document -> document;
      case Map<?, ?> map -> {
        Document document = Document.createDocument();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          Object nested = entry.getValue();
          if (nested instanceof Map<?, ?> nestedMap) {
            document.put(String.valueOf(entry.getKey()), toDocumentValue(nestedMap));
          } else {
            document.put(String.valueOf(entry.getKey()), serializeForDocument(nested));
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
            if (nested instanceof Map<?, ?> nestedMap) {
              document.put(entry.getKey(), toDocumentValue(nestedMap));
            } else {
              document.put(entry.getKey(), nested);
            }
          }
          yield document;
        }
        yield (Document) nitriteMapper.tryConvert(value, Document.class);
      }
    };
  }

  private Document toPersistedDocument(Document document) {
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
   * @param doc the Nitrite document
   * @param type the entity type
   * @param <T> the entity type
   * @return the hydrated entity
   */
  public <T> T fromDocument(final Document doc, final Class<T> type) {
    return fromDocumentInternal(doc, type, new HashMap<>());
  }

  @SuppressWarnings("unchecked")
  private <T> T fromDocumentInternal(final Document doc, final Class<T> type, final Map<String, Object> visited) {
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
            args[i] = val == null ? null : convertFromDocumentValue(val, arg);
        }
        entity = introspection.instantiate(args);
    } else {
        entity = introspection.instantiate();
    }

    // Add to visited BEFORE populating properties to handle back-references
    if (cacheKey != null) {
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

    // Populate properties
    for (RuntimePersistentProperty<T> prop : persistentEntity.getPersistentProperties()) {
        if (prop.isReadOnly() || prop.isAnnotationPresent(io.micronaut.data.annotation.Transient.class)) {
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

        if (value != null) {
            boolean associationStoredEmbedded = isAssociationStoredEmbedded(prop);
            if (prop instanceof RuntimeAssociation association && !associationStoredEmbedded) {
                if (helper != null) {
                    Class<Object> associatedType = association.getAssociatedEntity().getIntrospection().getBeanType();
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
            } else if (prop instanceof RuntimeAssociation association) {
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

    private static <T> boolean isAssociationStoredEmbedded(RuntimePersistentProperty<T> prop) {
        boolean associationStoredEmbedded;
        if (prop instanceof RuntimeAssociation<T> association && !association.isEmbedded()) {
            try {
                association.getAssociatedEntity().getIdentity();
                associationStoredEmbedded = false;
            } catch (IllegalStateException e) {
                associationStoredEmbedded = true;
            }
        } else {
            associationStoredEmbedded = prop instanceof RuntimeAssociation<T> association && association.isEmbedded();
        }
        return associationStoredEmbedded;
    }


    @SuppressWarnings("unchecked")
    private static <T> Class<T> castClass(Class<?> clazz) {
        return (Class<T>) clazz;
    }

    private static Object docGet(Document doc, String... keys) {
        for (String key : keys) {
            Object val = doc.get(key);
            if (val != null) return val;
        }
        return null;
    }
}
