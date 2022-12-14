package com.codahale.dropwizard;

import com.codahale.dropwizard.server.ServerFactory;
import com.codahale.dropwizard.logging.LoggingFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * An object representation of the YAML configuration file. Extend this with your own configuration
 * properties, and they'll be parsed from the YAML file as well.
 * <p/>
 * For example, given a YAML file with this:
 * <pre>
 * name: "Random Person"
 * age: 43
 * # ... etc ...
 * </pre>
 * And a configuration like this:
 * <pre>
 * public class ExampleConfiguration extends Configuration {
 *     \@NotNull
 *     private String name;
 *
 *     \@Min(1)
 *     \@Max(120)
 *     private int age;
 *
 *     public String getName() {
 *         return name;
 *     }
 *
 *     public int getAge() {
 *         return age;
 *     }
 * }
 * </pre>
 * Dropwizard will parse the given YAML file and provide an {@code ExampleConfiguration} instance
 * to your service whose {@code getName()} method will return {@code "Random Person"} and whose
 * {@code getAge()} method will return {@code 43}.
 *
 * @see <a href="http://www.yaml.org/YAML_for_ruby.html">YAML Cookbook</a>
 */
public class Configuration {
    @Valid
    @NotNull
    private ServerFactory server = new ServerFactory();

    @Valid
    @NotNull
    private LoggingFactory logging = new LoggingFactory();

    /**
     * Returns the server-specific section of the configuration file.
     *
     * @return server-specific configuration parameters
     */
    @JsonProperty("server")
    public ServerFactory getServerFactory() {
        return server;
    }

    /**
     * Sets the HTTP-specific section of the configuration file.
     */
    @JsonProperty("server")
    public void setServerFactory(ServerFactory factory) {
        this.server = factory;
    }

    /**
     * Returns the logging-specific section of the configuration file.
     *
     * @return logging-specific configuration parameters
     */
    @JsonProperty("logging")
    public LoggingFactory getLoggingFactory() {
        return logging;
    }

    /**
     * Sets the logging-specific section of the configuration file.
     */
    @JsonProperty("logging")
    public void setLoggingFactory(LoggingFactory factory) {
        this.logging = factory;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                      .add("server", server)
                      .add("logging", logging)
                      .toString();
    }
}
