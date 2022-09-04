package io.quarkus.hibernate.orm.deployment;

import java.util.Set;

import org.hibernate.jpa.boot.internal.ParsedPersistenceXmlDescriptor;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Not to be confused with PersistenceXmlDescriptorBuildItem, which holds
 * items of the same type.
 * This build item represents a later phase, and might include the implicit
 * configuration definitions that are automatically defined by Quarkus.
 */
public final class PersistenceUnitDescriptorBuildItem extends MultiBuildItem {

    private final ParsedPersistenceXmlDescriptor descriptor;

    public PersistenceUnitDescriptorBuildItem(ParsedPersistenceXmlDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public ParsedPersistenceXmlDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * Modifies the passed set by adding all explicitly listed classnames from this PU
     * into the set.
     * 
     * @param classNames the set to modify
     */
    public void addListedEntityClassNamesTo(Set<String> classNames) {
        classNames.addAll(descriptor.getManagedClassNames());
    }

    public String getExplicitSqlImportScriptResourceName() {
        return descriptor.getProperties().getProperty("javax.persistence.sql-load-script-source");
    }

}
