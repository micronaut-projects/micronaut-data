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
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.NitriteOperationsHelper;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
   *
   * @param value the raw value
   * @return the normalized value
   */
  public Object toFilterValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character) {
      return value;
    }
    if (value instanceof Instant instant) {
      return epochNanos(instant);
    }
    if (value instanceof UUID uuid) {
      return uuid.toString();
    }
    if (value instanceof LocalDate localDate) {
      return localDate.toEpochDay();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return epochNanos(localDateTime.toInstant(ZoneOffset.UTC));
    }
    if (value instanceof LocalTime localTime) {
      return localTime.toNanoOfDay();
    }
    // BigDecimal should NOT be converted to String to preserve numeric comparison.
    // Nitrite can handle BigDecimal directly for numeric filters.
    if (value instanceof URL url) {
      return url.toString();
    }
    if (value instanceof URI uri) {
      return uri.toString();
    }
    if (value instanceof Charset charset) {
      return charset.name();
    }
    if (value instanceof Enum<?> e) {
      return e.name();
    }
    if (value instanceof Optional<?> opt) {
      return toFilterValue(opt.orElse(null));
    }
    
    Class<?> clazz = value.getClass();
    // Skip expensive entity registry lookups for JDK classes (Strings, Numbers, Collections, Arrays, etc.)
    if (clazz.getName().startsWith("java.")) {
        return value;
    }

    // If it's an entity, try to get its ID
    try {
        RuntimePersistentEntity<Object> entity = runtimeEntityRegistry.getEntity((Class<Object>) clazz);
        if (entity != null) {
            RuntimePersistentProperty<Object> idProp = entity.getIdentity();
            if (idProp != null) {
                Object idValue = idProp.getProperty().get(value);
                if (idValue != null) {
                    return toFilterValue(idValue);
                }
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
   * @param rawField the field name
   * @return the normalized value
   */
  public Object toNitriteFilterValue(final Object val, @Nullable final String rawField) {
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
    RuntimePersistentEntity<T> persistentEntity =
        (RuntimePersistentEntity<T>) runtimeEntityRegistry.getEntity(type);
    RuntimePersistentProperty<T> idProp = persistentEntity.getIdentity();
    if (idProp != null) {
      return idProp.getProperty().get(entity);
    }
    return null;
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
        RuntimePersistentProperty<?> idProperty = entity.getIdentity();
        if (idProperty != null && (idProperty.getName().equals(field) || "_id".equals(field) || "id".equals(field))) {
            return ID_FIELD;
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
    RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
    RuntimePersistentProperty<T> idProperty = persistentEntity.getIdentity();

    // In Nitrite, we consistently use ID_FIELD ("id") for the identity property.
    String idField = ID_FIELD;
    
    if (idProperty != null && idProperty.isAnnotationPresent(EmbeddedId.class) && id != null) {
      try {
        Document idDoc = Document.createDocument();
        BeanIntrospection<?> introspection = BeanIntrospection.getIntrospection(idProperty.getType());
        for (BeanProperty prop : introspection.getBeanProperties()) {
          Object val = prop.get(id);
          if (val != null) {
            String name = prop.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(prop.getName());
            idDoc.put(name, toFilterValue(val));
          }
        }
        return FluentFilter.where(idField).eq(idDoc);
      } catch (Exception ignored) {
      }
    }
    return eqWithNumericCoercion(persistentEntity, idField, toFilterValue(id), idField);
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
  public Filter eqWithNumericCoercion(final RuntimePersistentEntity<?> entity, final String field, final Object value, final String dottedPath) {
    if (LOG.isDebugEnabled()) {
        LOG.debug("eqWithNumericCoercion: field={}, value={}, type={}, dottedPath={}", field, value, (value != null ? value.getClass().getName() : "null"), dottedPath);
    }
    if (entity != null && value instanceof Number n) {
      RuntimePersistentProperty<?> property = entity.getPropertyByName(field);
      if (property != null) {
        Class<?> targetType = property.getType();
        // If the target type is a number, we can use metadata for precise coercion.
        if (Number.class.isAssignableFrom(targetType) || targetType.isPrimitive()) {
          Optional<?> converted = ((Optional<Object>) conversionService.convert(n, targetType));
          if (converted.isPresent()) {
            return FluentFilter.where(dottedPath).eq(converted.get());
          }
        }
      }
    }
    
    Filter base = FluentFilter.where(dottedPath).eq(value);
    if (!(value instanceof Number n) || value == null) {
      return base;
    }

    // Fallback if no precise type could be derived
    return Filter.or(
        base,
        FluentFilter.where(dottedPath).eq(n.longValue()),
        FluentFilter.where(dottedPath).eq(n.intValue()),
        FluentFilter.where(dottedPath).eq(n.doubleValue())
    );
  }

  /**
   * Convert an entity to a Nitrite Document.
   *
   * @param entity the entity instance
   * @return the Nitrite Document
   * @param <T> the entity type
   */
  @SuppressWarnings("unchecked")
  public <T> Document toDocument(final T entity) {
    if (entity == null) {
      return null;
    }
    return toDocumentInternal(entity, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  @SuppressWarnings("unchecked")
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
    
    // Entities with @JsonProperty("_id") cause Jackson to serialize the id as "_id".
    // Nitrite reserves "_id" for NitriteId — rename user's id to its property name to avoid InvalidIdException.
    Object reservedId = doc.get("_id");
    if (reservedId != null && !(reservedId instanceof NitriteId)) {
      doc.remove("_id");
      RuntimePersistentEntity<T> persistentEntity =
          (RuntimePersistentEntity<T>) runtimeEntityRegistry.getEntity(entity.getClass());
      RuntimePersistentProperty<T> idProp = persistentEntity.getIdentity();
      String idField = idProp != null ? idProp.getName() : "id";
      doc.put(idField, toFilterValue(reservedId));
    }

    RuntimePersistentEntity<T> persistentEntity =
        (RuntimePersistentEntity<T>) runtimeEntityRegistry.getEntity(entity.getClass());
    RuntimePersistentProperty<T> idProperty = persistentEntity.getIdentity();
    if (idProperty != null && idProperty.isAnnotationPresent(EmbeddedId.class)) {
      Object embeddedId = idProperty.getProperty().get(entity);
      if (embeddedId != null) {
        try {
          Document idDoc = (Document) nitriteMapper.tryConvert(embeddedId, Document.class);
          doc.put(idProperty.getName(), idDoc);
          for (String field : idDoc.getFields()) {
            if (field.equals(idProperty.getName())) {
              continue;
            }
            doc.put(field, toFilterValue(idDoc.get(field)));
          }
        } catch (Exception ignored) {
        }
      }
    }
    return doc;
  }

  private Document getEntityIdAsDocument(Object entity) {
      if (entity == null) {
          return null;
      }
      RuntimePersistentEntity<Object> persistentEntity = (RuntimePersistentEntity<Object>) runtimeEntityRegistry.getEntity(entity.getClass());
      RuntimePersistentProperty<Object> idProp = persistentEntity.getIdentity();
      if (idProp != null) {
          Object idValue = idProp.getProperty().get(entity);
          if (idValue != null) {
              Document idDoc = Document.createDocument();
              idDoc.put(ID_FIELD, toFilterValue(idValue));
              return idDoc;
          }
      }
      return null;
  }

  /**
   * Classify the serialization strategy for a non-association property from its declared type.
   * Called once per property during meta construction; never on the hot path.
   */
  @SuppressWarnings("unchecked")
  private PropertyStrategy classifyValueStrategy(Class<?> type) {
    if (geometryClass != null && geometryClass.isAssignableFrom(type)) {
      return PropertyStrategy.GEOMETRY;
    }
    if (type == String.class || type == Boolean.class || type == Character.class) {
      return PropertyStrategy.JAVA_PASSTHROUGH;
    }
    if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
      return PropertyStrategy.JAVA_PASSTHROUGH;
    }
    if (type == Instant.class) {
      return PropertyStrategy.INSTANT;
    }
    if (type == UUID.class) {
      return PropertyStrategy.UUID;
    }
    if (type == LocalDate.class) {
      return PropertyStrategy.LOCAL_DATE;
    }
    if (type == LocalDateTime.class) {
      return PropertyStrategy.LOCAL_DATETIME;
    }
    if (type == LocalTime.class) {
      return PropertyStrategy.LOCAL_TIME;
    }
    if (type == URL.class) {
      return PropertyStrategy.URL;
    }
    if (type == URI.class) {
      return PropertyStrategy.URI;
    }
    if (type == Charset.class) {
      return PropertyStrategy.CHARSET;
    }
    if (type == Optional.class) {
      return PropertyStrategy.OPTIONAL;
    }
    if (type.isEnum()) {
      return PropertyStrategy.ENUM;
    }
    if (type.isArray()) {
      return PropertyStrategy.JAVA_PASSTHROUGH;
    }
    if (type.getPackageName().startsWith("java.")) {
      return PropertyStrategy.JAVA_PASSTHROUGH;
    }
    try {
      RuntimePersistentEntity<Object> entity = runtimeEntityRegistry.getEntity((Class<Object>) type);
      if (entity != null && entity.getIdentity() != null) {
        return PropertyStrategy.ENTITY_ID_REF;
      }
    } catch (Exception ignored) {
    }
    // @Introspected POJOs have a compile-time generated BeanIntrospection.
    // Use it directly — no Serde codec required, no reflection, no JSON round-trip.
    if (BeanIntrospector.SHARED.findIntrospection(type).isPresent()) {
      return PropertyStrategy.INTROSPECTED_POJO;
    }
    return PropertyStrategy.SERDE;
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
  @SuppressWarnings({"unchecked", "rawtypes"})
  private <T> NitriteEntityMeta<T> buildEntityMeta(RuntimePersistentEntity<T> persistentEntity) {
    List<WritablePropertyMeta<T>> writableList = new ArrayList<>();
    List<WritablePropertyMeta<T>> mappedByList = new ArrayList<>();

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
          }
        } else if (assoc.isEmbedded()) {
          strategy = PropertyStrategy.ASSOCIATION_EMBEDDED;
        } else {
          boolean isCollection = Iterable.class.isAssignableFrom(prop.getType());
          strategy = isCollection ? PropertyStrategy.ASSOCIATION_IDS_REF : PropertyStrategy.ASSOCIATION_ID_REF;
          RuntimePersistentProperty<?> idPropRaw = assoc.getAssociatedEntity().getIdentity();
          if (idPropRaw != null) {
            associatedIdProp = (BeanProperty<Object, Object>) idPropRaw.getProperty();
          }
        }
      } else {
        strategy = classifyValueStrategy(prop.getType());
        if (strategy == PropertyStrategy.ENTITY_ID_REF) {
          try {
            RuntimePersistentEntity<Object> refEntity = runtimeEntityRegistry.getEntity((Class<Object>) prop.getType());
            if (refEntity != null && refEntity.getIdentity() != null) {
              associatedIdProp = (BeanProperty<Object, Object>) refEntity.getIdentity().getProperty();
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

    return new NitriteEntityMeta<>(
        List.copyOf(writableList),
        List.copyOf(mappedByList),
        persistentEntity.getIdentity(),
        persistentEntity.getVersion());
  }

  /**
   * Return the cached {@link NitriteEntityMeta} for {@code type}, building it on first access.
   * After the first call per entity type, no registry or annotation lookups are performed.
   *
   * @param <T> the entity type
   * @param type the entity class
   * @return the cached entity metadata
   */
  @SuppressWarnings("unchecked")
  public <T> NitriteEntityMeta<T> getOrBuildMeta(Class<T> type) {
    return (NitriteEntityMeta<T>) entityMetaCache.computeIfAbsent(
        type, k -> buildEntityMeta(runtimeEntityRegistry.getEntity((Class<T>) k)));
  }

  /**
   * Convert entity to Document, handling Geometry fields specially.
   * Geometry objects must be preserved as-is for Nitrite's spatial module.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private <T> Document convertToDocumentInternal(T entity, Set<Object> visited) {
    if (entity == null) {
      return null;
    }

    NitriteEntityMeta<T> meta = getOrBuildMeta((Class<T>) entity.getClass());
    Document doc = Document.createDocument();

    for (WritablePropertyMeta<T> wpm : meta.writableProps()) {
      Object value = ((BeanProperty<T, Object>) wpm.prop().getProperty()).get(entity);
      if (value == null) {
        continue;
      }
      String fieldName = wpm.fieldName();
      switch (wpm.strategy()) {
        case JAVA_PASSTHROUGH -> doc.put(fieldName, value);
        case INSTANT         -> doc.put(fieldName, epochNanos((Instant) value));
        case UUID            -> doc.put(fieldName, value.toString());
        case ENUM            -> doc.put(fieldName, ((Enum<?>) value).name());
        case LOCAL_DATE      -> doc.put(fieldName, ((LocalDate) value).toEpochDay());
        case LOCAL_DATETIME  -> doc.put(fieldName, epochNanos(((LocalDateTime) value).toInstant(ZoneOffset.UTC)));
        case LOCAL_TIME      -> doc.put(fieldName, ((LocalTime) value).toNanoOfDay());
        case URL             -> doc.put(fieldName, value.toString());
        case URI             -> doc.put(fieldName, value.toString());
        case CHARSET         -> doc.put(fieldName, ((Charset) value).name());
        case GEOMETRY        -> doc.put(fieldName, value);
        case OPTIONAL        -> {
          Object inner = ((Optional<?>) value).orElse(null);
          if (inner != null) {
            doc.put(fieldName, toFilterValue(inner));
          }
        }
        case ENTITY_ID_REF   -> {
          if (wpm.associatedIdProp() != null) {
            Object idValue = wpm.associatedIdProp().get(value);
            if (idValue != null) {
              doc.put(fieldName, toFilterValue(idValue));
            }
          }
        }
        case INTROSPECTED_POJO -> doc.put(fieldName, pojoToMap(value));
        case SERDE           -> {
          try {
            String json = serdeObjectMapper.writeValueAsString(value);
            doc.put(fieldName, serdeObjectMapper.readValue(json, Object.class));
          } catch (Exception e) {
            doc.put(fieldName, value);
          }
        }
        case ASSOCIATION_EMBEDDED ->
          doc.put(fieldName, convertAssociation(value, (RuntimeAssociation) wpm.prop(), visited));
        case ASSOCIATION_ID_REF   -> {
          if (wpm.associatedIdProp() != null) {
            Object idValue = wpm.associatedIdProp().get(value);
            if (idValue != null) {
              doc.put(fieldName, toFilterValue(idValue));
            }
          }
        }
        case ASSOCIATION_IDS_REF  -> {
          List<Object> ids = new ArrayList<>();
          for (Object item : (Iterable<?>) value) {
            if (item != null && wpm.associatedIdProp() != null) {
              Object idValue = wpm.associatedIdProp().get(item);
              if (idValue != null) {
                ids.add(toFilterValue(idValue));
              }
            }
          }
          if (!ids.isEmpty()) {
            doc.put(fieldName, ids);
          }
        }
        case ASSOCIATION_MAPPED_BY -> {
          // Skip - back-reference only, nothing to store
        }
        default -> throw new IllegalStateException("Unknown property strategy: " + wpm.strategy());
      }
    }

    // Identity
    RuntimePersistentProperty<T> idProp = meta.idProp();
    if (idProp != null) {
      Object idValue = ((BeanProperty<T, Object>) idProp.getProperty()).get(entity);
      if (idValue != null) {
        doc.put(ID_FIELD, toFilterValue(idValue));
      }
    }

    // Version
    RuntimePersistentProperty<T> versionProp = meta.versionProp();
    if (versionProp != null) {
      Object versionValue = ((BeanProperty<T, Object>) versionProp.getProperty()).get(entity);
      if (versionValue != null) {
        doc.put(versionProp.getPersistedName(), toFilterValue(versionValue));
      }
    }

    return doc;
  }

  private Object convertAssociation(Object value, RuntimeAssociation association, Set<Object> visited) {
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

  private Object convertSingleAssociation(Object value, RuntimeAssociation association, Set<Object> visited) {
      if (value == null) {
          return null;
      }
      
      if (visited.contains(value)) {
          RuntimePersistentEntity<?> associatedEntity = (RuntimePersistentEntity<?>) association.getAssociatedEntity();
          RuntimePersistentProperty<?> idProp = associatedEntity.getIdentity();
          if (idProp != null) {
              @SuppressWarnings("rawtypes")
              BeanProperty property = idProp.getProperty();
              @SuppressWarnings("unchecked")
              Object idValue = property.get(value);
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
  @SuppressWarnings("unchecked")
  private Map<String, Object> pojoToMap(Object pojo) {
    BeanIntrospection<Object> intro =
        BeanIntrospector.SHARED.getIntrospection((Class<Object>) pojo.getClass());
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
  @SuppressWarnings("unchecked")
  private <P> P mapToPojo(Map<?, ?> map, BeanIntrospection<P> intro) {
    P pojo = intro.instantiate();
    for (BeanProperty<P, Object> p : intro.getBeanProperties()) {
      if (p.isReadOnly()) {
        continue;
      }
      Object v = map.get(p.getName());
      if (v == null) {
        continue;
      }
      if (v instanceof Map<?, ?> nested) {
        BeanIntrospector.SHARED.findIntrospection(p.getType()).ifPresentOrElse(
            ni -> p.set(pojo, mapToPojo(nested, (BeanIntrospection<Object>) ni)),
            () -> p.set(pojo, conversionService.convert(v, p.asArgument()).orElse(null))
        );
      } else {
        p.set(pojo, conversionService.convert(v, p.asArgument()).orElse(null));
      }
    }
    return pojo;
  }

  /**
   * Check if an object is a JTS Geometry. Uses the class reference cached at construction time.
   */
  private boolean isGeometry(Object value) {
    return value != null && geometryClass != null && geometryClass.isInstance(value);
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
    // Reverse the epoch-number storage format written by toFilterValue.
    // Nitrite's Jackson may deserialise stored longs as Integer, Long, or Double
    // depending on magnitude and mapper configuration, so we accept any Number.
    if (value instanceof Number n) {
      Class<?> t = target.getType();
      if (t == Instant.class) {
        return fromEpochNanos(n.longValue());
      }
      if (t == LocalDate.class) {
        return LocalDate.ofEpochDay(n.longValue());
      }
      if (t == LocalDateTime.class) {
        return LocalDateTime.ofInstant(fromEpochNanos(n.longValue()), ZoneOffset.UTC);
      }
      if (t == LocalTime.class) {
        return LocalTime.ofNanoOfDay(n.longValue());
      }
    }
    // Map → POJO: prefer BeanIntrospection (no Serde codec required) when available,
    // fall back to Serde for types with custom codecs but no @Introspected metadata.
    if (value instanceof Map<?, ?> mapValue && !Map.class.isAssignableFrom(target.getType())) {
      Optional<BeanIntrospection<Object>> maybeIntro =
          BeanIntrospector.SHARED.findIntrospection((Class<Object>) target.getType());
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
   * Encodes an Instant as nanoseconds since the Unix epoch, preserving full precision.
   *
   * @param instant the instant to encode
   * @return nanoseconds since the Unix epoch
   */
  public static long epochNanos(Instant instant) {
    return Math.addExact(
        Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L),
        instant.getNano());
  }

  /**
   * Reverses {@link #epochNanos(Instant)}.
   *
   * @param nanos nanoseconds since the Unix epoch
   * @return the corresponding Instant
   */
  public static Instant fromEpochNanos(long nanos) {
    return Instant.ofEpochSecond(
        Math.floorDiv(nanos, 1_000_000_000L),
        (int) Math.floorMod(nanos, 1_000_000_000L));
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
        cacheKey = type.getName() + ":" + id.toString();
        if (visited.containsKey(cacheKey)) {
            return (T) visited.get(cacheKey);
        }
    }

    if (LOG.isDebugEnabled()) {
        LOG.debug("fromDocumentInternal: type={}, doc={}", type.getName(), doc);
    }
    RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
    BeanIntrospection<T> introspection = persistentEntity.getIntrospection();
    
    T entity;
    Argument<?>[] ctorArgs = introspection.getConstructorArguments();
    if (ctorArgs.length > 0) {
        Object[] args = new Object[ctorArgs.length];
        for (int i = 0; i < ctorArgs.length; i++) {
            Argument<?> arg = ctorArgs[i];
            String name = arg.getName();
            RuntimePersistentProperty<T> prop = persistentEntity.getPropertyByName(name);
            String storedName = prop != null ? prop.getPersistedName() : name;
            
            RuntimePersistentProperty<T> idProp = persistentEntity.getIdentity();
            if (idProp != null && idProp.getName().equals(name)) {
                storedName = ID_FIELD;
            }
            
            Object val = doc.get(storedName);
            if (val == null && !storedName.equals(name)) {
                val = doc.get(name);
            }
            if (val == null && storedName.equals(ID_FIELD)) {
                val = doc.get("_id");
            }
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
    RuntimePersistentProperty<T> idProp = persistentEntity.getIdentity();
    if (idProp != null) {
        Object storedId = doc.get(ID_FIELD);
        if (storedId == null) {
            storedId = doc.get(idProp.getPersistedName());
        }
        if (storedId == null) {
            storedId = doc.get(idProp.getName());
        }
        if (storedId == null) {
            storedId = doc.get("_id");
        }
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

        BeanProperty<T, Object> property = (BeanProperty<T, Object>) prop.getProperty();
        Object value = doc.get(storedName);
        if (value == null && !storedName.equals(prop.getName())) {
            value = doc.get(prop.getName());
        }
        if (value == null && storedName.equals(ID_FIELD)) {
            value = doc.get("_id");
        }

        if (value != null) {
            if (prop instanceof RuntimeAssociation association && !association.isEmbedded()) {
                if (helper != null) {
                    Class<?> associatedType = association.getAssociatedEntity().getIntrospection().getBeanType();
                    if (value instanceof Iterable<?> ids) {
                        List<Object> associatedEntities = new ArrayList<>();
                        for (Object associatedId : ids) {
                            Document associatedDoc = helper.getCollection(associatedType).find(idEqualsFilter((Class<Object>) associatedType, associatedId)).firstOrNull();
                            if (associatedDoc != null) {
                                associatedEntities.add(fromDocumentInternal(associatedDoc, (Class<Object>) associatedType, visited));
                            }
                        }
                        property.set(entity, conversionService.convert(associatedEntities, property.asArgument()).orElse(null));
                        continue;
                    } else if (!(value instanceof Document)) {
                        // It's a single ID
                        Document associatedDoc = helper.getCollection(associatedType).find(idEqualsFilter((Class<Object>) associatedType, value)).firstOrNull();
                        if (associatedDoc != null) {
                            property.set(entity, fromDocumentInternal(associatedDoc, (Class<Object>) associatedType, visited));
                            continue;
                        }
                    }
                }
            } else if (prop instanceof RuntimeAssociation association && association.isEmbedded()) {
                if (value instanceof Document embeddedDoc) {
                    property.set(entity, fromDocumentInternal(embeddedDoc, (Class<Object>) association.getAssociatedEntity().getIntrospection().getBeanType(), visited));
                    continue;
                } else if (value instanceof List<?> list) {
                    List<Object> embeddedEntities = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Document d) {
                            embeddedEntities.add(fromDocumentInternal(d, (Class<Object>) association.getAssociatedEntity().getIntrospection().getBeanType(), visited));
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
    RuntimePersistentProperty<T> versionProp = persistentEntity.getVersion();
    if (versionProp != null && !versionProp.isReadOnly()) {
        BeanProperty<T, Object> versionProperty = (BeanProperty<T, Object>) versionProp.getProperty();
        String versionStoredName = versionProp.getPersistedName();
        Object versionValue = doc.get(versionStoredName);
        if (versionValue == null && !versionStoredName.equals(versionProp.getName())) {
            versionValue = doc.get(versionProp.getName());
        }
        // Version can be 0, so check for null explicitly
        if (versionValue != null) {
            Object convertedVersion = convertFromDocumentValue(versionValue, versionProperty.asArgument());
            if (convertedVersion != null) {
                versionProperty.set(entity, convertedVersion);
            }
        }
    }

    // Trigger postLoad event
    runtimeEntityRegistry.getEntityEventListener().postLoad(
        (io.micronaut.data.event.EntityEventContext<Object>) new io.micronaut.data.runtime.event.DefaultEntityEventContext<>(persistentEntity, entity)
    );

    return entity;
  }

  // ========== Pre-computed entity metadata ==========

  /**
   * Serialization strategy for a persistent property, classified once from its declared type
   * at meta-build time. The dispatch loop uses this to avoid instanceof chains and registry
   * lookups on every document conversion.
   */
  public enum PropertyStrategy {
    JAVA_PASSTHROUGH,
    INSTANT,
    UUID,
    ENUM,
    LOCAL_DATE,
    LOCAL_DATETIME,
    LOCAL_TIME,
    URL,
    URI,
    CHARSET,
    OPTIONAL,
    ENTITY_ID_REF,
    GEOMETRY,
    INTROSPECTED_POJO,
    SERDE,
    ASSOCIATION_MAPPED_BY,
    ASSOCIATION_EMBEDDED,
    ASSOCIATION_ID_REF,
    ASSOCIATION_IDS_REF
  }

  /**
   * Per-property metadata pre-computed once per entity type. Read-only and {@code @Transient}
   * properties are excluded at build time and never appear here.
   * {@link PropertyStrategy#ASSOCIATION_MAPPED_BY} entries are excluded from
   * {@link NitriteEntityMeta#writableProps()} and appear only in
   * {@link NitriteEntityMeta#mappedByAssocs()}.
   *
   * @param <T> the entity type
   * @param prop the runtime persistent property
   * @param fieldName the persisted field name
   * @param strategy the serialization strategy
   * @param mappedBy the mappedBy value for back-references (if applicable)
   * @param associatedIdProp the ID property accessor for association references (if applicable)
   * @param backRefProperty the back-reference property setter (if applicable)
   */
  public record WritablePropertyMeta<T>(
      RuntimePersistentProperty<T> prop,
      String fieldName,
      PropertyStrategy strategy,
      @Nullable String mappedBy,
      @Nullable BeanProperty<Object, Object> associatedIdProp,
      @Nullable BeanProperty<Object, Object> backRefProperty
  ) { }

  /**
   * Pre-computed metadata for one entity type, cached in {@link #entityMetaCache}.
   *
   * @param <T> the entity type
   * @param writableProps the list of writable properties
   * @param mappedByAssocs the list of mapped-by associations
   * @param idProp the identity property (if any)
   * @param versionProp the version property (if any)
   */
  public record NitriteEntityMeta<T>(
      List<WritablePropertyMeta<T>> writableProps,
      List<WritablePropertyMeta<T>> mappedByAssocs,
      @Nullable RuntimePersistentProperty<T> idProp,
      @Nullable RuntimePersistentProperty<T> versionProp
  ) { }
}
