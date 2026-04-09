package com.example.repository;

import com.example.entity.EntityWithMapField;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

@Repository
public interface EntityWithMapFieldRepository extends JpaRepository<EntityWithMapField, Long> {
}
