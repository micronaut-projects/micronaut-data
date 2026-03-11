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
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.runtime.NitriteOperationsHelper;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteId;
import org.dizitart.no2.common.mapper.NitriteMapper;
import org.dizitart.no2.common.tuples.Pair;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
  private final NitriteMapper nitriteMapper;
  private final RuntimeEntityRegistry runtimeEntityRegistry;
  private NitriteOperationsHelper helper;

  /**
   * Create a new mapper.
   *
   * @param conversionService the conversion service
   * @param nitriteMapper the Nitrite mapper
   * @param runtimeEntityRegistry the runtime entity registry
   */
  public NitriteEntityMapper(
      final ConversionService conversionService,
      final NitriteMapper nitriteMapper,
      final RuntimeEntityRegistry runtimeEntityRegistry) {
    this.conversionService = conversionService;
    this.nitriteMapper = nitriteMapper;
    this.runtimeEntityRegistry = runtimeEntityRegistry;
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
    if (value instanceof Instant instant) {
      return instant.toString();
    }
    if (value instanceof UUID uuid) {
      return uuid.toString();
    }
    if (value instanceof LocalDate localDate) {
      return localDate.toString();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.toString();
    }
    if (value instanceof LocalTime localTime) {
      return localTime.toString();
    }
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal.toString();
    }
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
    
    // If it's an entity, try to get its ID
    try {
        RuntimePersistentEntity<Object> entity = runtimeEntityRegistry.getEntity((Class<Object>) value.getClass());
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
        if (Number.class.isAssignableFrom(targetType) || targetType.isPrimitive()) {
          return new NumericFilter(field, n);
        }
      }
    }
    return FluentFilter.where(field).eq(value);
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
   * Convert entity to Document, handling Geometry fields specially.
   * Geometry objects must be preserved as-is for Nitrite's spatial module.
   */
  @SuppressWarnings("unchecked")
  private <T> Document convertToDocumentInternal(T entity, Set<Object> visited) {
    if (entity == null) {
      return null;
    }
    
    RuntimePersistentEntity<T> persistentEntity =
        (RuntimePersistentEntity<T>) runtimeEntityRegistry.getEntity(entity.getClass());
    
    // Manual conversion to respect Micronaut Data's naming strategy and handle associations/spatial types.
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
            if (association.isEmbedded()) {
                doc.put(fieldName, convertAssociation(value, association, visited));
            } else {
                // For non-embedded associations, we store the ID(s) EXCEPT if it's mappedBy
                String mappedBy = association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
                if (mappedBy != null) {
                    continue;
                }
                
                if (value instanceof Iterable<?> iterable) {
                    List<Object> ids = new ArrayList<>();
                    RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
                    RuntimePersistentProperty<?> idProp = associatedEntity.getIdentity();
                    if (idProp != null) {
                        for (Object item : iterable) {
                            if (item != null) {
                                Object idValue = ((BeanProperty<Object, Object>) idProp.getProperty()).get(item);
                                if (idValue != null) {
                                    ids.add(toFilterValue(idValue));
                                }
                            }
                        }
                    }
                    if (!ids.isEmpty()) {
                        doc.put(fieldName, ids);
                    }
                } else {
                    RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
                    RuntimePersistentProperty<?> idProp = associatedEntity.getIdentity();
                    if (idProp != null) {
                        Object idValue = ((BeanProperty<Object, Object>) idProp.getProperty()).get(value);
                        if (idValue != null) {
                            doc.put(fieldName, toFilterValue(idValue));
                        }
                    }
                }
            }
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
            doc.put(ID_FIELD, toFilterValue(idValue));
        }
    }
    
    // Handle version
    RuntimePersistentProperty<T> versionProp = persistentEntity.getVersion();
    if (versionProp != null) {
        @SuppressWarnings("rawtypes")
        BeanProperty beanProperty = versionProp.getProperty();
        @SuppressWarnings("unchecked")
        Object versionValue = beanProperty.get(entity);
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
   * Check if an object is a JTS Geometry using reflection.
   */
  private boolean isGeometry(Object value) {
    if (value == null) return false;
    return ClassUtils.isPresent(GEOMETRY_CLASS, value.getClass().getClassLoader()) &&
           ClassUtils.forName(GEOMETRY_CLASS, value.getClass().getClassLoader())
               .map(c -> c.isInstance(value)).orElse(false);
  }

  /**
   * Check if a type is a simple type that should not be converted to a Document.
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
   */
  public Object convertFromDocumentValue(Object value, Argument<?> target) {
    if (value == null) {
      return null;
    }
    if (target.getType().isInstance(value)) {
      return value;
    }
    return conversionService.convert(value, target).orElse(null);
  }

  /**
   * Hydrate an entity from a Nitrite Document.
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
        } else {
            // Hydrate mappedBy associations
            if (prop instanceof RuntimeAssociation association && !association.isEmbedded() && helper != null) {
                String mappedBy = association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
                if (mappedBy != null && (association.getKind() == Relation.Kind.ONE_TO_MANY || association.getKind() == Relation.Kind.MANY_TO_MANY)) {
                    Object idValue = getEntityIdValue(entity, (Class<Object>) type);
                    if (idValue != null) {
                        Class<?> associatedType = association.getAssociatedEntity().getIntrospection().getBeanType();
                        RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
                        RuntimePersistentProperty<?> backProp = associatedEntity.getPropertyByName(mappedBy);
                        if (backProp != null) {
                            String backFieldName = backProp.getPersistedName();
                            // If the back-prop is the identity, use "id"
                            if (associatedEntity.getIdentity() != null && associatedEntity.getIdentity().equals(backProp)) {
                                backFieldName = ID_FIELD;
                            }
                            
                            Filter filter = FluentFilter.where(backFieldName).eq(toFilterValue(idValue));
                            List<Document> childDocs = helper.getCollection(associatedType).find(filter).toList();
                            List<Object> children = new ArrayList<>();
                            for (Document childDoc : childDocs) {
                                children.add(fromDocumentInternal(childDoc, (Class<Object>) associatedType, visited));
                            }
                            property.set(entity, conversionService.convert(children, property.asArgument()).orElse(null));
                        }
                    }
                }
            }
        }
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


  /**
   * Internal filter for numeric equality.
   */
  private static class NumericFilter implements Filter {
    private final String field;
    private final Number value;

    NumericFilter(String field, Number value) {
      this.field = field;
      this.value = value;
    }

    @Override
    public boolean apply(Pair<NitriteId, Document> element) {
      Object val = element.getSecond().get(field);
      if (val instanceof Number n) {
        return n.doubleValue() == value.doubleValue();
      }
      return false;
    }

    @Override
    public String toString() {
      return "(" + field + " == " + value + ")";
    }
  }
}
