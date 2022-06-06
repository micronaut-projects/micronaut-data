package io.micronaut.data.hibernate;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.listeners.PrePersistEventListener;
import io.micronaut.data.event.listeners.PreUpdateEventListener;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import jakarta.inject.Singleton;

@Factory
public class AuditCompanyListener {

    private static final String createUser = "CREATEADMIN";
    private static final String updateUser = "UPDATEADMIN";

    @Singleton
    PrePersistEventListener<AuditCompany> beforeEntityPersist() {
        return new PrePersistEventListener<AuditCompany>() {

            @Override
            public boolean prePersist(AuditCompany entity) {
                entity.setCreateUser(createUser);
                entity.setUpdateUser(createUser);
                return false;
            }

            @Override
            public boolean prePersist(EntityEventContext<AuditCompany> context) {
                AuditCompany entity = context.getEntity();
                prePersist(entity);
                RuntimePersistentEntity<AuditCompany> persistentEntity = context.getPersistentEntity();
                BeanProperty<AuditCompany, Object> prop1 = (BeanProperty<AuditCompany, Object>) persistentEntity.getPropertyByName("createUser").getProperty();
                BeanProperty<AuditCompany, Object> prop2 = (BeanProperty<AuditCompany, Object>) persistentEntity.getPropertyByName("updateUser").getProperty();
                context.setProperty(prop1, prop1.get(entity));
                context.setProperty(prop2, prop2.get(entity));
                return true;
            }
        };
    }

    @Singleton
    PreUpdateEventListener<AuditCompany> beforeEntityUpdate() {
        return new PreUpdateEventListener<AuditCompany>() {
            @Override
            public boolean preUpdate(AuditCompany auditContact) {
                auditContact.setUpdateUser(updateUser);
                return true;
            }
        };
    }

}
