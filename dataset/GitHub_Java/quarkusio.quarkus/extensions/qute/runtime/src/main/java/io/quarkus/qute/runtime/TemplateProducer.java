package io.quarkus.qute.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkus.qute.Expression;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.Variant;
import io.quarkus.qute.api.ResourcePath;

@Singleton
public class TemplateProducer {

    private static final Logger LOGGER = Logger.getLogger(TemplateProducer.class);

    @Inject
    EngineProducer engineProducer;

    @Produces
    Template getDefaultTemplate(InjectionPoint injectionPoint) {
        String name = null;
        if (injectionPoint.getMember() instanceof Field) {
            // For "@Inject Template items" use "items"
            name = injectionPoint.getMember().getName();
        } else {
            AnnotatedParameter<?> parameter = (AnnotatedParameter<?>) injectionPoint.getAnnotated();
            if (parameter.getJavaParameter().isNamePresent()) {
                name = parameter.getJavaParameter().getName();
            } else {
                name = injectionPoint.getMember().getName();
                LOGGER.warnf("Parameter name not present - using the method name as the template name instead %s", name);
            }
        }
        // Note that engine may not be initialized and so we inject a delegating template
        return new InjectableTemplate(name);
    }

    @Produces
    @ResourcePath("ignored")
    Template getTemplate(InjectionPoint injectionPoint) {
        ResourcePath path = null;
        for (Annotation qualifier : injectionPoint.getQualifiers()) {
            if (qualifier.annotationType().equals(ResourcePath.class)) {
                path = (ResourcePath) qualifier;
                break;
            }
        }
        if (path == null || path.value().isEmpty()) {
            throw new IllegalStateException("No template reource path specified");
        }
        // Note that engine may not be initialized and so we inject a delegating template
        return new InjectableTemplate(path.value());
    }

    class InjectableTemplate implements Template {

        private final String path;

        public InjectableTemplate(String path) {
            this.path = path;
        }

        private Template template() {
            Template template = engineProducer.getEngine().getTemplate(path);
            if (template == null) {
                throw new IllegalStateException("No template found for path: " + path);
            }
            return template;
        }

        @Override
        public TemplateInstance instance() {
            return template().instance();
        }

        @Override
        public Set<Expression> getExpressions() {
            return template().getExpressions();
        }

        @Override
        public String getGeneratedId() {
            return template().getGeneratedId();
        }

        @Override
        public Optional<Variant> getVariant() {
            return template().getVariant();
        }

    }

}
