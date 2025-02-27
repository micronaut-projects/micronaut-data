package io.micronaut.data.tck.repositories;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Document;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends CrudRepository<Document, UUID> {

    @Override
    @Join(value = "type", type = Join.Type.LEFT_FETCH)
   // @Query("SELECT document_.`id`,document_.`name`,document_type_.`id` AS type_id ,document_type_.`name` AS type_name,document_type_.`deleted` AS type_deleted FROM `document` document_ LEFT JOIN `document_type` document_type_ ON document_.`type_id`=document_type_.`id` AND document_type_.deleted = false WHERE (document_.`id` = :id)")
    @NonNull Optional<Document> findById(@NonNull UUID id);
}
