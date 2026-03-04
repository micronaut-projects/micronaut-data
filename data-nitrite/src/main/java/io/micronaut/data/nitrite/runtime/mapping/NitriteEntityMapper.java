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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteId;
import org.dizitart.no2.common.mapper.NitriteMapper;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Helper for mapping between Micronaut Data entities and Nitrite Documents.
 *
 * @since 1.0.0
 */
@Internal
public final class NitriteEntityMapper {

  private final ConversionService conversionService;
  private final NitriteMapper nitriteMapper;
  private final RuntimeEntityRegistry runtimeEntityRegistry;

  /**
   * Create a new entity mapper.
   *
   * @param conversionService the conversion service
   * @param nitriteMapper the Nitrite mapper
   * @param runtimeEntityRegistry the runtime entity registry
   */
  public NitriteEntityMapper(
      ConversionService conversionService,
      NitriteMapper nitriteMapper,
      RuntimeEntityRegistry runtimeEntityRegistry) {
    this.conversionService = conversionService;
    this.nitriteMapper = nitriteMapper;
    this.runtimeEntityRegistry = runtimeEntityRegistry;
  }

  /**
   * Normalize a value for use in a Nitrite Filter.
   *
   * @param value the raw value
   * @return the normalized value
   */
  public Object toFilterValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Instant instant) {
      // Match the mapping used by the Nitrite Jackson mapper: store/query Instants as an
      // epoch-second double (keeps equality and ordering comparisons consistent).
      return instant.getEpochSecond() + instant.getNano() / 1_000_000_000.0;
    }
    if (value instanceof UUID uuid) {
      return uuid.toString();
    }
    if (value instanceof Enum<?> e) {
      return e.name();
    }
    return value;
  }

  /**
   * Normalize a query parameter for use in Nitrite filters, including Micronaut Data embedded-id
   * values.
   *
   * @param val the raw value
   * @return the normalized value
   */
  public Object toNitriteFilterValue(final Object val) {
    Object normalized = toFilterValue(val);
    if (normalized == null) {
      return null;
    }
    if (normalized.getClass().isAnnotationPresent(Embeddable.class)) {
      Object converted = nitriteMapper.tryConvert(normalized, Document.class);
      if (converted instanceof Document doc) {
        for (String field : doc.getFields()) {
          doc.put(field, toFilterValue(doc.get(field)));
        }
        return doc;
      }
    }
    return normalized;
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
    RuntimePersistentProperty<T> idProperty = persistentEntity.getIdentity();
    if (idProperty == null) {
      return null;
    }
    return idProperty.getProperty().get(entity);
  }

  /**
   * Update the ID of an entity instance.
   *
   * @param property the ID property
   * @param entity the entity instance
   * @param id the new ID value
   * @return the updated entity instance
   * @param <T> the entity type
   */
  public <T> T updateEntityId(BeanProperty<T, Object> property, T entity, Object id) {
    return property.withValue(entity, conversionService.convertRequired(id, property.getType()));
  }

  /**
   * Create a Nitrite Filter for ID equality with numeric coercion.
   *
   * @param type the entity class
   * @param id the ID value
   * @return the Nitrite Filter
   * @param <T> the entity type
   */
  public <T> Filter idEqualsFilter(final Class<T> type, final Object id) {
    RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
    RuntimePersistentProperty<T> idProperty = persistentEntity.getIdentity();
    String idField = "id";
    if (idProperty != null && idProperty.isAnnotationPresent(EmbeddedId.class) && id != null) {
      try {
        Document idDoc = (Document) nitriteMapper.tryConvert(id, Document.class);
        List<Filter> parts = new ArrayList<>();
        for (String field : idDoc.getFields()) {
          parts.add(eqWithNumericCoercion(field, toFilterValue(idDoc.get(field))));
        }
        if (parts.isEmpty()) {
          return Filter.ALL;
        }
        if (parts.size() == 1) {
          return parts.get(0);
        }
        return Filter.and(parts.toArray(new Filter[0]));
      } catch (Exception ignored) {
        return eqWithNumericCoercion(idField, toFilterValue(id));
      }
    }
    return eqWithNumericCoercion(idField, toFilterValue(id));
  }

  /**
   * Normalize a field name.
   *
   * @param field the raw field name
   * @return the normalized field name
   */
  public String normalizeFieldName(final String field) {
    return "_id".equals(field) ? "id" : field;
  }

  /**
   * Build an equality filter that tolerates numeric representation differences.
   *
   * @param field the field name
   * @param value the comparison value
   * @return the Nitrite Filter
   */
  public Filter eqWithNumericCoercion(final String field, final Object value) {
    Filter base = FluentFilter.where(field).eq(value);
    if (!(value instanceof Number n) || value == null) {
      return base;
    }

    List<Filter> filters = new ArrayList<>();
    filters.add(base);

    try {
      BigDecimal asBigDecimal = new BigDecimal(n.toString());
      filters.add(FluentFilter.where(field).eq(asBigDecimal));

      if (asBigDecimal.stripTrailingZeros().scale() <= 0) {
        long asLong = asBigDecimal.longValue();
        filters.add(FluentFilter.where(field).eq(asLong));
        if (asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE) {
          filters.add(FluentFilter.where(field).eq((int) asLong));
        }
      } else {
        filters.add(FluentFilter.where(field).eq(asBigDecimal.doubleValue()));
      }
    } catch (Exception ignored) {
    }

    if (filters.size() == 1) {
      return base;
    }
    return Filter.or(filters.toArray(new Filter[0]));
  }

  /**
   * Convert entity to Nitrite Document.
   *
   * @param entity the entity instance
   * @return the Nitrite Document
   * @param <T> the entity type
   */
  @SuppressWarnings("unchecked")
  public <T> Document toDocument(final T entity) {
    Document doc = (Document) nitriteMapper.tryConvert(entity, Document.class);
    // Entities with @JsonProperty("_id") cause Jackson to serialize the id as "_id".
    // Nitrite reserves "_id" for NitriteId — rename user's id to "id" to avoid InvalidIdException.
    Object reservedId = doc.get("_id");
    if (reservedId != null && !(reservedId instanceof NitriteId)) {
      doc.remove("_id");
      doc.put("id", toFilterValue(reservedId));
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

  /**
   * Convert Nitrite Document to entity.
   *
   * @param doc the Nitrite Document
   * @param type the entity class
   * @return the entity instance
   * @param <T> the entity type
   */
  @SuppressWarnings("unchecked")
  public <T> T fromDocument(final Document doc, final Class<T> type) {
    // Create a copy of the document using only user-visible fields (doc.getFields() excludes
    // Nitrite internals: "_id"/NitriteId, "_revision", "_modified", "_source").
    // This prevents entities with @JsonProperty("_id") from getting the NitriteId (a numeric long)
    // where they expect a UUID, which would cause ObjectMappingException.
    Document docCopy = Document.createDocument();
    for (String field : doc.getFields()) {
      docCopy.put(field, doc.get(field));
    }
    T entity;
    RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
    RuntimePersistentProperty<T> idProp = persistentEntity.getIdentity();

    try {
      entity = (T) nitriteMapper.tryConvert(docCopy, type);
    } catch (Exception e) {
      if (idProp != null && idProp.isAnnotationPresent(EmbeddedId.class)) {
        Document retryDoc = Document.createDocument();
        for (RuntimePersistentProperty<T> p : persistentEntity.getPersistentProperties()) {
          String name = p.getName();
          if (name.equals(idProp.getName())) {
            continue;
          }
          Object v = doc.get(name);
          if (v != null) {
            retryDoc.put(name, v);
          }
        }
        entity = (T) nitriteMapper.tryConvert(retryDoc, type);
      } else {
        throw e;
      }
    }

    if (idProp != null && !idProp.isAnnotationPresent(EmbeddedId.class) && idProp.getProperty().get(entity) == null) {
      Object storedId = doc.get("id");
      if (storedId == null) {
          storedId = doc.get("_id");
      }
      if (storedId != null && !(storedId instanceof NitriteId)) {
        idProp.getProperty().set(entity, convertIdValue(storedId, idProp.getType()));
      }
    }

    if (idProp != null
        && idProp.isAnnotationPresent(EmbeddedId.class)
        && idProp.getProperty().get(entity) == null) {
      reconstructEmbeddedId(doc, entity, idProp);
    }
    return entity;
  }

  private <T> void reconstructEmbeddedId(Document doc, T entity, RuntimePersistentProperty<T> idProp) {
    try {
      Class<?> embeddedIdType = idProp.getType();
      BeanIntrospection<?> idIntrospection = BeanIntrospection.getIntrospection(embeddedIdType);
      Object embeddedId = null;

      Argument<?>[] ctorArgs = idIntrospection.getConstructorArguments();
      if (ctorArgs.length > 0) {
        Object[] values = new Object[ctorArgs.length];
        for (int i = 0; i < ctorArgs.length; i++) {
          String name = ctorArgs[i].getName();
          Optional<? extends BeanProperty<?, ?>> property = idIntrospection.getProperty(name);
          String storedName = property
              .flatMap(p -> p.getAnnotationMetadata()
                  .stringValue(MappedProperty.class)
                  .filter(v -> !v.isBlank()))
              .orElse(name);
          Object storedValue = doc.get(storedName);
          values[i] = conversionService.convertRequired(storedValue, ctorArgs[i]);
        }
        embeddedId = idIntrospection.instantiate(values);
      } else {
        embeddedId = idIntrospection.instantiate();
        for (BeanProperty<?, ?> property : idIntrospection.getBeanProperties()) {
          if (property.isReadOnly()) {
            continue;
          }
          String storedName = property.getAnnotationMetadata()
              .stringValue(MappedProperty.class)
              .filter(v -> !v.isBlank())
              .orElse(property.getName());
          Object storedValue = doc.get(storedName);
          if (storedValue != null) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            BeanProperty raw = (BeanProperty) property;
            raw.set(embeddedId, conversionService.convertRequired(storedValue, property.asArgument()));
          }
        }
      }

      if (embeddedId == null) {
        Document embeddedIdDoc = Document.createDocument();
        for (BeanProperty<?, ?> property : idIntrospection.getBeanProperties()) {
          String storedName = property.getAnnotationMetadata()
              .stringValue(MappedProperty.class)
              .filter(v -> !v.isBlank())
              .orElse(property.getName());
          Object storedValue = doc.get(storedName);
          if (storedValue != null) {
            embeddedIdDoc.put(property.getName(), storedValue);
          }
        }
        embeddedId = nitriteMapper.tryConvert(embeddedIdDoc, embeddedIdType);
      }
      if (embeddedId != null) {
        idProp.getProperty().set(entity, embeddedId);
      }
    } catch (Exception ignored) {
    }
  }

  private Object convertIdValue(final Object stored, final Class<?> targetType) {
    if (targetType == UUID.class && stored instanceof String s) {
      return UUID.fromString(s);
    }
    return stored;
  }

  /**
   * Create an association document.
   *
   * @param persistentEntity owner entity metadata
   * @param value owner entity instance
   * @param childPersistentEntity associated entity metadata
   * @param child associated entity instance
   * @return the association document
   */
  public Document association(
      RuntimePersistentEntity<Object> persistentEntity,
      Object value,
      RuntimePersistentEntity<Object> childPersistentEntity,
      Object child) {
    Document document = Document.createDocument();
    document.put(
        persistentEntity.getPersistedName(),
        toFilterValue(getEntityIdValue(value, (Class<Object>) value.getClass())));
    document.put(
        childPersistentEntity.getPersistedName(),
        toFilterValue(getEntityIdValue(child, (Class<Object>) child.getClass())));
    return document;
  }
}
