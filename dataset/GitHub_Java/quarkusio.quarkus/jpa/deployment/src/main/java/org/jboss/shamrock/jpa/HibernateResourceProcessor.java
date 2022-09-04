package org.jboss.shamrock.jpa;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.boot.archive.scan.spi.ClassDescriptor;
import org.hibernate.jpa.boot.internal.ParsedPersistenceXmlDescriptor;
import org.hibernate.protean.impl.PersistenceUnitsHolder;
import org.jboss.shamrock.deployment.ArchiveContext;
import org.jboss.shamrock.deployment.ProcessorContext;
import org.jboss.shamrock.deployment.ResourceProcessor;
import org.jboss.shamrock.deployment.RuntimePriority;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.shamrock.jpa.runtime.JPADeploymentTemplate;
import org.jboss.shamrock.jpa.runtime.ShamrockScanner;

/**
 * Simulacrum of JPA bootstrap.
 * <p>
 * This does not address the proper integration with Hibernate
 * Rather prepare the path to providing the right metadata
 *
 * @author Emmanuel Bernard emmanuel@hibernate.org
 * @author Sanne Grinovero  <sanne@hibernate.org>
 */
public final class HibernateResourceProcessor implements ResourceProcessor {

    @Override
    public void process(final ArchiveContext archiveContext, final ProcessorContext processorContext) throws Exception {

        List<ParsedPersistenceXmlDescriptor> descriptors = PersistenceUnitsHolder.loadOriginalXMLParsedDescriptors();

        // Hibernate specific reflective classes; these are independent from the model and configuration details.
        HibernateReflectiveNeeds.registerStaticReflectiveNeeds(processorContext);

        JpaJandexScavenger scavenger = new JpaJandexScavenger(archiveContext, processorContext, descriptors);
        final KnownDomainObjects domainObjects = scavenger.discoverModelAndRegisterForReflection();

        //Modify the bytecode of all entities to enable lazy-loading, dirty checking, etc..
        enhanceEntities(domainObjects, archiveContext, processorContext);

        ShamrockScanner scanner = new ShamrockScanner();
        Set<ClassDescriptor> classDescriptors = new HashSet<>();
        for (String i : domainObjects.getClassNames()) {
            ShamrockScanner.ClassDescriptorImpl desc = new ShamrockScanner.ClassDescriptorImpl(i, ClassDescriptor.Categorization.MODEL);
            classDescriptors.add(desc);
        }
        scanner.setClassDescriptors(classDescriptors);
        PersistenceUnitsHolder.initializeJpa(descriptors, scanner);

        try (BytecodeRecorder recorder = processorContext.addStaticInitTask(RuntimePriority.JPA_DEPLOYMENT)) {
            recorder.registerNonDefaultConstructor(ParsedPersistenceXmlDescriptor.class.getDeclaredConstructor(URL.class), (i) -> Collections.singletonList(i.getPersistenceUnitRootUrl()));
            recorder.getRecordingProxy(JPADeploymentTemplate.class).initMetadata(descriptors, scanner);
        }


    }

    private void enhanceEntities(final KnownDomainObjects domainObjects, ArchiveContext archiveContext, ProcessorContext processorContext) {
        processorContext.addByteCodeTransformer(new HibernateEntityEnhancer(domainObjects));
    }


    @Override
    public int getPriority() {
        // Because we are the best
        return 1;
    }
}
