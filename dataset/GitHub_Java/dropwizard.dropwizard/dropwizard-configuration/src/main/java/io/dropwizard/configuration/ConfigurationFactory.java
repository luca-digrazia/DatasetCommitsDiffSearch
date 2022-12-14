package io.dropwizard.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException;
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.YAMLException;
import com.google.common.base.Splitter;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A factory class for loading YAML configuration files, binding them to configuration objects, and
 * and validating their constraints. Allows for overriding configuration parameters from system
 * properties.
 *
 * @param <T> the type of the configuration objects to produce
 */
public class ConfigurationFactory<T> {
    private final Class<T> klass;
    private final String propertyPrefix;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final YAMLFactory yamlFactory;

    /**
     * Creates a new configuration factory for the given class.
     *
     * @param klass          the configuration class
     * @param validator      the validator to use
     * @param objectMapper   the Jackson {@link ObjectMapper} to use
     * @param propertyPrefix the system property name prefix used by overrides
     */
    public ConfigurationFactory(Class<T> klass,
                                Validator validator,
                                ObjectMapper objectMapper,
                                String propertyPrefix) {
        this.klass = klass;
        this.propertyPrefix = propertyPrefix.endsWith(".") ? propertyPrefix : propertyPrefix + '.';
        this.mapper = objectMapper.copy();
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
        this.yamlFactory = new YAMLFactory();
    }

    /**
     * Loads, parses, binds, and validates a configuration object.
     *
     * @param provider the provider to to use for reading configuration files
     * @param path     the path of the configuration file
     * @return a validated configuration object
     * @throws IOException            if there is an error reading the file
     * @throws ConfigurationException if there is an error parsing or validating the file
     */
    public T build(ConfigurationSourceProvider provider, String path) throws IOException, ConfigurationException {
        try (InputStream input = provider.open(checkNotNull(path))) {
            final JsonNode node = mapper.readTree(yamlFactory.createParser(input));
            return build(node, path);
        } catch (YAMLException e) {
            ConfigurationParsingException.Builder builder = ConfigurationParsingException
                    .builder("Malformed YAML")
                    .setCause(e)
                    .setDetail(e.getMessage());

            if (e instanceof MarkedYAMLException) {
                builder.setLocation(((MarkedYAMLException) e).getProblemMark());
            }

            throw builder.build(path);
        }
    }

    /**
     * Loads, parses, binds, and validates a configuration object from a file.
     *
     * @param file the path of the configuration file
     * @return a validated configuration object
     * @throws IOException            if there is an error reading the file
     * @throws ConfigurationException if there is an error parsing or validating the file
     */
    public T build(File file) throws IOException, ConfigurationException {
        return build(new FileConfigurationSourceProvider(), file.toString());
    }

    /**
     * Loads, parses, binds, and validates a configuration object from an empty document.
     *
     * @return a validated configuration object
     * @throws IOException            if there is an error reading the file
     * @throws ConfigurationException if there is an error parsing or validating the file
     */
    public T build() throws IOException, ConfigurationException {
        return build(JsonNodeFactory.instance.objectNode(), "default configuration");
    }

    private T build(JsonNode node, String path) throws IOException, ConfigurationException {
        for (Map.Entry<Object, Object> pref : System.getProperties().entrySet()) {
            final String prefName = (String) pref.getKey();
            if (prefName.startsWith(propertyPrefix)) {
                final String configName = prefName.substring(propertyPrefix.length());
                addOverride(node, configName, System.getProperty(prefName));
            }
        }

        try {
            final T config = mapper.readValue(new TreeTraversingParser(node), klass);
            validate(path, config);
            return config;
        } catch (UnrecognizedPropertyException e) {
            Collection<Object> knownProperties = e.getKnownPropertyIds();
            List<String> properties = new ArrayList<>(knownProperties.size());
            for (Object property : knownProperties) {
                properties.add(property.toString());
            }
            throw ConfigurationParsingException.builder("Unrecognized field")
                    .setFieldPath(e.getPath())
                    .setLocation(e.getLocation())
                    .addSuggestions(properties)
                    .setSuggestionBase(e.getPropertyName())
                    .setCause(e)
                    .build(path);
        } catch (InvalidFormatException e) {
            String sourceType = e.getValue().getClass().getSimpleName();
            String targetType = e.getTargetType().getSimpleName();
            throw ConfigurationParsingException.builder("Incorrect type of value")
                    .setDetail("is of type: " + sourceType + ", expected: " + targetType)
                    .setLocation(e.getLocation())
                    .setFieldPath(e.getPath())
                    .setCause(e)
                    .build(path);
        } catch (JsonMappingException e) {
            throw ConfigurationParsingException.builder("Failed to parse configuration")
                    .setDetail(e.getMessage())
                    .setFieldPath(e.getPath())
                    .setLocation(e.getLocation())
                    .setCause(e)
                    .build(path);
        }
    }

    private void addOverride(JsonNode root, String name, String value) {
        JsonNode node = root;
        final Iterator<String> keys = Splitter.on('.').trimResults().split(name).iterator();
        while (keys.hasNext()) {
            final String key = keys.next();
            if (!(node instanceof ObjectNode)) {
                throw new IllegalArgumentException("Unable to override " + name + "; it's not a valid path.");
            }
            final ObjectNode obj = (ObjectNode) node;
            
            if (keys.hasNext()) {
                JsonNode child = obj.get(key);
                if (child == null) {
                    child = obj.objectNode();
                    obj.put(key, child);
                }
                node = child;
            } else {
                if (obj.get(key) != null && obj.get(key).isArray()) {
                    final Iterator<String> values = Splitter.on('|').trimResults().split(value).iterator();
                    ArrayNode arrayNode = (ArrayNode) obj.get(key);
                    arrayNode.removeAll();
                    while (values.hasNext())
                        arrayNode.add (values.next()); 
                }
                else {
                    obj.put(key, value);
                }
            }
        }
    }

    private void validate(String path, T config) throws ConfigurationValidationException {
        final Set<ConstraintViolation<T>> violations = validator.validate(config);
        if (!violations.isEmpty()) {
            throw new ConfigurationValidationException(path, violations);
        }
    }
}
