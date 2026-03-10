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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteId;
import org.dizitart.no2.common.mapper.NitriteMapper;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Helper for mapping between Micronaut Data entities and Nitrite Documents.
 *
 * @since 1.0.0
 */
@Internal
public final class NitriteEntityMapper {

  private static final Logger LOG = LoggerFactory.getLogger(NitriteEntityMapper.class);
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
      return instant.toString();
    }
    if (value instanceof java.util.Date date) {
      return date.toInstant().toString();
    }
    if (value instanceof UUID uuid) {
      return uuid.toString();
    }
    if (value instanceof Enum<?> e) {
      return e.name();
    }
    if (value instanceof URL || value instanceof URI || value instanceof Charset) {
        return value.toString();
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
  public Object toNitriteFilterValue(final Object val, final String fieldPath) {
    Object normalized = toFilterValue(val);
    if (normalized == null) {
      return null;
    }
    
    // If we have a dotted path like "projectId.departmentId" and the value is the @Embeddable/@Introspected object,
    // we need to extract the leaf value.
    if (fieldPath != null && fieldPath.contains(".")) {
        String leafProperty = fieldPath.substring(fieldPath.lastIndexOf('.') + 1);
        try {
            BeanIntrospection<?> introspection = BeanIntrospection.getIntrospection(normalized.getClass());
            Optional<? extends BeanProperty<?, ?>> prop = introspection.getProperty(leafProperty);
            if (prop.isPresent()) {
                Object extracted = ((BeanProperty<Object, Object>) prop.get()).get(normalized);
                return toFilterValue(extracted);
            }
        } catch (Exception ignored) {
            // Introspection might fail if the object is just a Map or not introspected, 
            // which is fine, we fall back to the normalized value.
        }
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

  public Object toNitriteFilterValue(final Object val) {
    return toNitriteFilterValue(val, null);
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
   * @return the Nitrite Filter
   */
  public <T> Filter idEqualsFilter(final Class<T> type, final Object id) {
    RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
    RuntimePersistentProperty<T> idProperty = persistentEntity.getIdentity();

    // In Nitrite, we need to find the field name used in the Document.
    // Try in order:
    // 1. The persisted name (mapped by AP)
    // 2. If persisted name is _id, try property name (Nitrite avoids _id)
    // 3. Fallback to property name
    String idField = "id";
    if (idProperty != null) {
        String persistedName = idProperty.getPersistedName();
        if ("_id".equals(persistedName)) {
            idField = idProperty.getName();
        } else {
            // For TCK BasicTypes, getPersistedName() might be returning "id"
            // even though the property is "myId".
            idField = persistedName;
        }
    }
    
    if (idProperty != null && idProperty.isAnnotationPresent(EmbeddedId.class) && id != null) {
      try {
        Document idDoc = Document.createDocument();
        BeanIntrospection<?> introspection = BeanIntrospection.getIntrospection(idProperty.getType());
        
        for (BeanProperty<?, ?> prop : introspection.getBeanProperties()) {
            Object val = ((BeanProperty<Object, Object>) prop).get(id);
            if (val != null) {
                idDoc.put(prop.getName(), val);
            }
        }
        
        List<Filter> parts = new ArrayList<>();
        RuntimePersistentEntity<?> idEntity = runtimeEntityRegistry.getEntity(idProperty.getType());
        for (String field : idDoc.getFields()) {
          String dottedPath = idProperty.getName() + "." + field;
          parts.add(eqWithNumericCoercion(idEntity, field, toFilterValue(idDoc.get(field)), dottedPath));
        }
        if (parts.isEmpty()) {
          return Filter.ALL;
        }
        if (parts.size() == 1) {
          return parts.get(0);
        }
        return Filter.and(parts.toArray(new Filter[0]));
      } catch (Exception ignored) {
        return eqWithNumericCoercion(persistentEntity, idField, toFilterValue(id), idField);
      }
    }
    return eqWithNumericCoercion(persistentEntity, idField, toFilterValue(id), idField);
  }

  public boolean isSimpleType(Class<?> type) {
    return ClassUtils.isJavaLangType(type) || 
           type.isPrimitive() || 
           Number.class.isAssignableFrom(type) ||
           type == UUID.class ||
           type == Instant.class;
  }

  /**
   * Normalize a field name.
   *
   * @param field the raw field name
   * @param entity the entity metadata
   * @return the normalized field name
   */
  public String normalizeFieldName(final String field, final RuntimePersistentEntity<?> entity) {
    if (entity != null) {
        // Handle common document store conventions (_id or id) 
        // that might be passed by the Query Builder.
        if ("_id".equals(field) || "id".equals(field)) {
            RuntimePersistentProperty<?> idProperty = entity.getIdentity();
            if (idProperty != null) {
                // If it's the identity, we use the property name in Nitrite.
                return idProperty.getName();
            }
        }
    }
    return "_id".equals(field) ? "id" : field;
  }

  /**
   * Normalize a field name.
   *
   * @param field the raw field name
   * @return the normalized field name
   */
  public String normalizeFieldName(final String field) {
    return normalizeFieldName(field, null);
  }

  /**
   * Build an equality filter that tolerates numeric representation differences.
   *
   * @param field the field name
   * @param value the comparison value
   * @return the Nitrite Filter
   */
  public Filter eqWithNumericCoercion(final String field, final Object value) {
    return eqWithNumericCoercion(null, field, value, field);
  }

  /**
   * Build an equality filter that tolerates numeric representation differences,
   * using entity metadata for precision when available.
   *
   * @param entity the entity metadata
   * @param field the field name (internal to entity)
   * @param value the comparison value
   * @param dottedPath the full path to the field in Nitrite (e.g. "projectId.departmentId")
   * @return the Nitrite Filter
   */
  public Filter eqWithNumericCoercion(final RuntimePersistentEntity<?> entity, final String field, final Object value, final String dottedPath) {
    if (entity != null && value instanceof Number n) {
      RuntimePersistentProperty<?> property = entity.getPropertyByName(field);
      if (property != null) {
        Class<?> targetType = property.getType();
        // If the target type is a number, we can use metadata for precise coercion.
        // If it's NOT a number (e.g. Instant), then toFilterValue already converted
        // the query parameter to the storage format (e.g. Double epoch),
        // and we must NOT convert it back to the entity type or comparisons will fail.
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

    // Fallback to "shotgun" expansion for safety (e.g. dynamic queries),
    // but with deduplication to ensure clean logs and execution.
    List<Filter> filters = new ArrayList<>();
    filters.add(base);

    try {
      BigDecimal asBigDecimal = new BigDecimal(n.toString());
      addIfMissing(filters, FluentFilter.where(dottedPath).eq(asBigDecimal));

      if (asBigDecimal.stripTrailingZeros().scale() <= 0) {
        long asLong = asBigDecimal.longValue();
        addIfMissing(filters, FluentFilter.where(dottedPath).eq(asLong));
        if (asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE) {
          addIfMissing(filters, FluentFilter.where(dottedPath).eq((int) asLong));
        }
      } else {
        addIfMissing(filters, FluentFilter.where(dottedPath).eq(asBigDecimal.doubleValue()));
      }
    } catch (Exception ignored) {
    }

    if (filters.size() == 1) {
      return filters.get(0);
    }
    return Filter.or(filters.toArray(new Filter[0]));
  }

  private void addIfMissing(List<Filter> filters, Filter filter) {
    String s = filter.toString();
    for (Filter f : filters) {
      if (f.toString().equals(s)) {
        return;
      }
    }
    filters.add(filter);
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
    if (entity == null) {
      return null;
    }
    // Check if entity has Geometry fields that need special handling
    Document doc = convertToDocument(entity);
    
    // Break infinite recursion for bi-directional relationships
    sanitizeDocument(doc, Collections.newSetFromMap(new IdentityHashMap<>()));

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

  private void sanitizeDocument(Object obj, Set<Object> visited) {
    if (obj == null || !visited.add(obj)) {
      return;
    }
    if (obj instanceof Document doc) {
      for (String field : doc.getFields()) {
        Object value = doc.get(field);
        if (value != null && (value instanceof Document || value instanceof Iterable || value instanceof Map)) {
            sanitizeDocument(value, visited);
        }
      }
    } else if (obj instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        sanitizeDocument(item, visited);
      }
    } else if (obj instanceof Map<?, ?> map) {
      for (Object value : map.values()) {
        sanitizeDocument(value, visited);
      }
    }
  }

  /**
   * Convert entity to Document, handling Geometry fields specially.
   * Geometry objects must be preserved as-is for Nitrite's spatial module.
   */
  @SuppressWarnings("unchecked")
  private <T> Document convertToDocument(T entity) {
    if (entity == null) {
      return null;
    }
    
    RuntimePersistentEntity<T> persistentEntity =
        (RuntimePersistentEntity<T>) runtimeEntityRegistry.getEntity(entity.getClass());
    
    // Check if the entity has any complex associations that might cause recursion
    boolean hasAssociations = false;
    for (RuntimePersistentProperty<T> prop : persistentEntity.getPersistentProperties()) {
        if (prop instanceof RuntimeAssociation) {
            hasAssociations = true;
            break;
        }
    }

    if (!hasAssociations) {
        // Safe to use standard mapper for simple entities
        Document doc = (Document) nitriteMapper.tryConvert(entity, Document.class);
        
        // Remove transient properties that Nitrite's default mapper might have included
        for (RuntimePersistentProperty<T> prop : persistentEntity.getPersistentProperties()) {
            if (prop.isAnnotationPresent(io.micronaut.data.annotation.Transient.class) || 
                prop.getProperty().isAnnotationPresent(io.micronaut.data.annotation.Transient.class)) {
                doc.remove(prop.getPersistedName());
                doc.remove(prop.getName());
            }
        }
        
        // Preserving Geometry objects if present
        for (RuntimePersistentProperty<T> prop : persistentEntity.getPersistentProperties()) {
            if (!prop.isReadOnly()) {
                Object value = prop.getProperty().get(entity);
                if (value != null && isGeometry(value)) {
                    doc.put(prop.getName(), value);
                }
            }
        }
        return doc;
    }

    // Manual conversion to protect against infinite recursion in bi-directional relations
    Document doc = Document.createDocument();
    for (RuntimePersistentProperty<T> prop : persistentEntity.getPersistentProperties()) {
        if (prop.isReadOnly() || 
            prop.isAnnotationPresent(io.micronaut.data.annotation.Transient.class) ||
            prop.getProperty().isAnnotationPresent(io.micronaut.data.annotation.Transient.class)) {
            continue;
        }
        @SuppressWarnings("rawtypes")
        BeanProperty beanProperty = prop.getProperty();
        @SuppressWarnings("unchecked")
        Object value = beanProperty.get(entity);
        if (value == null) {
            continue;
        }
        String fieldName = prop.getPersistedName();
        
        if (prop instanceof RuntimeAssociation association) {
            // For associations, we only store a "reference" (the ID) or a shallow copy
            // to prevent the StackOverflow seen in TCK (Author -> Books -> Author).
            doc.put(fieldName, convertAssociation(value, association));
        } else if (isGeometry(value)) {
            doc.put(fieldName, value);
        } else {
            doc.put(fieldName, toFilterValue(value));
        }
    }
    
    // Handle identity
    RuntimePersistentProperty<T> idProp = persistentEntity.getIdentity();
    if (idProp != null) {
        @SuppressWarnings("rawtypes")
        BeanProperty beanProperty = idProp.getProperty();
        @SuppressWarnings("unchecked")
        Object idValue = beanProperty.get(entity);
        if (idValue != null) {
            doc.put(idProp.getName(), toFilterValue(idValue));
        }
    }
    
    return doc;
  }

  private Object convertAssociation(Object value, RuntimeAssociation association) {
      if (value == null) {
          return null;
      }
      if (value instanceof Iterable<?> iterable) {
          List<Object> list = new ArrayList<>();
          for (Object item : iterable) {
              list.add(convertSingleAssociation(item, association));
          }
          return list;
      }
      return convertSingleAssociation(value, association);
  }

  private Object convertSingleAssociation(Object value, RuntimeAssociation association) {
      if (value == null) {
          return null;
      }
      RuntimePersistentEntity<?> associatedEntity = (RuntimePersistentEntity<?>) association.getAssociatedEntity();
      RuntimePersistentProperty<?> idProp = associatedEntity.getIdentity();
      
      if (idProp != null) {
          @SuppressWarnings("rawtypes")
          BeanProperty property = idProp.getProperty();
          @SuppressWarnings("unchecked")
          Object idValue = property.get(value);
          if (idValue != null) {
              // Store just the ID for the association to break recursion
              return toFilterValue(idValue);
          }
      }
      
      // Fallback: if no ID, try shallow convert or toString
      try {
          return nitriteMapper.tryConvert(value, Document.class);
      } catch (Exception e) {
          return value.toString();
      }
  }

  /**
   * Check if an object is a JTS Geometry using reflection.
   */
  private boolean isGeometry(Object value) {
    try {
      Class<?> geometryClass = Class.forName("org.locationtech.jts.geom.Geometry");
      return geometryClass.isInstance(value);
    } catch (ClassNotFoundException e) {
      return false;
    }
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
    if (doc == null) {
        return null;
    }
    RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
    BeanIntrospection<T> introspection = persistentEntity.getIntrospection();
    
    T entity;
    Argument<?>[] ctorArgs = introspection.getConstructorArguments();
    if (ctorArgs.length == 0) {
        entity = introspection.instantiate();
    } else {
        Object[] args = new Object[ctorArgs.length];
        for (int i = 0; i < ctorArgs.length; i++) {
            Argument<?> arg = ctorArgs[i];
            String name = arg.getName();
            RuntimePersistentProperty<T> prop = persistentEntity.getPropertyByName(name);
            String storedName = prop != null ? prop.getPersistedName() : name;
            Object val = doc.get(storedName);
            if (val == null) {
                val = doc.get(name);
            }
            if (val == null && name.equals("id")) {
                val = doc.get("_id");
            }
            args[i] = val == null ? null : convertFromDocumentValue(val, arg);
        }
        entity = introspection.instantiate(args);
    }

    // Populate properties
    for (RuntimePersistentProperty<T> prop : persistentEntity.getPersistentProperties()) {
        if (prop.isReadOnly() || prop.isAnnotationPresent(io.micronaut.data.annotation.Transient.class) ||
            prop.getProperty().isAnnotationPresent(io.micronaut.data.annotation.Transient.class)) {
            continue;
        }
        
        @SuppressWarnings("rawtypes")
        BeanProperty property = prop.getProperty();
        String storedName = prop.getPersistedName();
        Object value = doc.get(storedName);
        if (value == null) {
            value = doc.get(prop.getName());
        }
        
        if (value != null) {
            if (prop instanceof RuntimeAssociation association) {
                Object converted = convertFromDocumentValue(value, property.asArgument());
                property.set(entity, converted);
            } else {
                property.set(entity, convertFromDocumentValue(value, property.asArgument()));
            }
        }
    }

    RuntimePersistentProperty<T> idProp = persistentEntity.getIdentity();
    if (idProp != null) {
        Object storedId = doc.get(idProp.getPersistedName());
        if (storedId == null) {
            storedId = doc.get(idProp.getName());
        }
        if (storedId == null) {
            storedId = doc.get("id");
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

    if (idProp != null && idProp.isAnnotationPresent(EmbeddedId.class) && idProp.getProperty().get(entity) == null) {
        reconstructEmbeddedId(doc, entity, idProp);
    }
    
    return entity;
  }

  private Object convertFromDocumentValue(Object value, Argument<?> target) {
      if (value == null) {
          return null;
      }
      Class<?> targetType = target.getType();
      if (targetType.isInstance(value)) {
          return value;
      }
      
      if (value instanceof Document docValue) {
          if (Map.class.isAssignableFrom(targetType)) {
              return docValue;
          }
          // Try Micronaut way first if introspection is available
          try {
              if (BeanIntrospector.SHARED.findIntrospection(targetType).isPresent()) {
                  return fromDocument(docValue, (Class<Object>) targetType);
              }
          } catch (Exception e) {
              LOG.warn("Failed to map nested document using Micronaut introspection for type {}: {}", targetType, e.getMessage());
          }
          
          // Fallback to Nitrite's own mapping for plain POJOs
          try {
              return nitriteMapper.tryConvert(docValue, targetType);
          } catch (Exception e) {
              LOG.warn("Failed to map nested document using NitriteMapper for type {}: {}", targetType, e.getMessage());
          }
          return conversionService.convert(value, target).orElse(null);
      }

      if (value instanceof List<?> list && (targetType.isAssignableFrom(List.class) || targetType.isAssignableFrom(Set.class))) {
          Argument<?> elementType = target.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
          List<Object> convertedList = new ArrayList<>();
          for (Object item : list) {
              convertedList.add(convertFromDocumentValue(item, elementType));
          }
          if (targetType.isAssignableFrom(Set.class)) {
              return new HashSet<>(convertedList);
          }
          return convertedList;
      }

      if (value instanceof String s) {
          if (targetType == java.util.Date.class) {
              try {
                  return java.util.Date.from(Instant.parse(s));
              } catch (Exception e) {
                  // Fall back to conversion service
              }
          }
          if (targetType == Instant.class) {
              try {
                  return Instant.parse(s);
              } catch (Exception e) {
                  // Fall back
              }
          }
      }

      return conversionService.convert(value, target).orElse(null);
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
    } catch (Exception e) {
        LOG.warn("Failed to reconstruct EmbeddedId for entity {}: {}", entity.getClass(), e.getMessage());
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
