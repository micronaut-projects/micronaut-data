package example.graal;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import javax.management.ObjectName;
import javax.persistence.TableGenerator;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import org.hibernate.boot.archive.spi.InputStreamAccess;
import org.hibernate.boot.jaxb.internal.MappingBinder;
import org.hibernate.boot.jaxb.spi.Binding;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.bytecode.spi.BytecodeProvider;
import org.hibernate.event.spi.EventType;
import org.hibernate.id.Assigned;
import org.hibernate.id.ForeignGenerator;
import org.hibernate.id.GUIDGenerator;
import org.hibernate.id.IdentityGenerator;
import org.hibernate.id.IncrementGenerator;
import org.hibernate.id.SelectGenerator;
import org.hibernate.id.UUIDGenerator;
import org.hibernate.id.UUIDHexGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.jmx.spi.JmxService;
import org.hibernate.service.Service;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.spi.Manageable;
import org.hibernate.service.spi.Stoppable;
import org.hibernate.type.EnumType;

import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.annotation.TypeHint.AccessType;

// Additional classes
@TypeHint(
    typeNames = {
        "org.hibernate.internal.CoreMessageLogger_$logger",
        "org.hibernate.internal.EntityManagerMessageLogger_$logger",
        "org.hibernate.annotations.common.util.impl.Log_$logger",
        "com.sun.xml.internal.stream.events.XMLEventFactoryImpl"
    }
)
final class Loggers {}

@TypeHint({
    UUIDGenerator.class,
    GUIDGenerator.class,
    UUIDHexGenerator.class,
    Assigned.class,
    IdentityGenerator.class,
    SelectGenerator.class,
    SequenceStyleGenerator.class,
    IncrementGenerator.class,
    ForeignGenerator.class,
    TableGenerator.class
})
final class IdGenerators {}

// Disable Runtime Byte Code Enhancement
@TargetClass(className = "org.hibernate.cfg.Environment")
@TypeHint(
    value = {EventType.class, EnumType.class}, 
    accessType = {AccessType.ALL_DECLARED_FIELDS, AccessType.ALL_DECLARED_METHODS, AccessType.ALL_DECLARED_CONSTRUCTORS}
)
final class EnvironmentSubs {
    @Substitute
    public static BytecodeProvider buildBytecodeProvider(Properties properties) {
        return new org.hibernate.bytecode.internal.none.BytecodeProviderImpl();
    }
}

// Disable JMX support
@TargetClass(className = "org.hibernate.jmx.internal.JmxServiceImpl")
@Substitute
final class NoopJmxService implements JmxService, Stoppable {

    @Substitute
    public NoopJmxService(Map configValues) {
    }

    @Override
    public void stop() {

    }

    @Override
    public void registerService(Manageable service, Class<? extends Service> serviceRole) {

    }

    @Override
    public void registerMBean(ObjectName objectName, Object mBean) {

    }
}

// Disable XML support
@TargetClass(className = "org.hibernate.boot.spi.XmlMappingBinderAccess")
@Substitute
final class NoopXmlMappingBinderAccess {

    @Substitute
    public NoopXmlMappingBinderAccess(ServiceRegistry serviceRegistry) {
    }

    @Substitute
    public MappingBinder getMappingBinder() {
        return null;
    }

    @Substitute
    public Binding bind(String resource) {
        return null;
    }

    @Substitute
    public Binding bind(File file) {
        return null;
    }

    @Substitute
    public Binding bind(InputStreamAccess xmlInputStreamAccess) {
        return null;
    }

    @Substitute
    public Binding bind(InputStream xmlInputStream) {
        return null;
    }

    @Substitute
    public Binding bind(URL url) {
        return null;
    }
}

// Disable Schema Resolution
@TargetClass(className = "org.hibernate.boot.jaxb.internal.stax.LocalXmlResourceResolver")
@Substitute
final class NoopSchemaResolver implements XMLResolver {
    @Substitute
    public NoopSchemaResolver(ClassLoaderService classLoaderService) {
    }
    
    @Override
    @Substitute
    public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace)
            throws XMLStreamException {
        return null;
    }

}