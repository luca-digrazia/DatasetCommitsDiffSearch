package io.dropwizard;

import io.dropwizard.cli.CheckCommand;
import io.dropwizard.cli.Cli;
import io.dropwizard.cli.ServerCommand;
import io.dropwizard.logging.LoggingFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Generics;
import io.dropwizard.util.JarLocation;

/**
 * The base class for Dropwizard applications.
 *
 * @param <T> the type of configuration class for this application
 */
public abstract class Application<T extends Configuration> {
    static {
        // make sure spinning up Hibernate Validator doesn't yell at us
        LoggingFactory.bootstrap();
    }

    /**
     * Returns the {@link Class} of the configuration class type parameter.
     *
     * @return the configuration class
     * @see Generics#getTypeParameter(Class, Class)
     */
    public final Class<T> getConfigurationClass() {
        return Generics.getTypeParameter(getClass(), Configuration.class);
    }

    /**
     * Returns the name of the application.
     *
     * @return the application's name
     */
    public String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Initializes the application bootstrap.
     *
     * @param bootstrap the application bootstrap
     */
    public void initialize(Bootstrap<T> bootstrap) {
    }

    /**
     * When the application runs, this is called after the {@link Bundle}s are run. Override it to add
     * providers, resources, etc. for your application.
     *
     * @param configuration the parsed {@link Configuration} object
     * @param environment   the application's {@link Environment}
     * @throws Exception if something goes wrong
     */
    public abstract void run(T configuration, Environment environment) throws Exception;

    /**
     * Parses command-line arguments and runs the application. Call this method from a {@code public
     * static void main} entry point in your application.
     *
     * @param arguments the command-line arguments
     * @throws Exception if something goes wrong
     */
    public final void run(String[] arguments) throws Exception {
        final Bootstrap<T> bootstrap = new Bootstrap<>(this);
        bootstrap.addCommand(new ServerCommand<>(this));
        bootstrap.addCommand(new CheckCommand<>(this));
        initialize(bootstrap);
        final Cli cli = new Cli(new JarLocation(getClass()), bootstrap, System.out, System.err);
        if (!cli.run(arguments)) {
            // only exit if there's an error running the command
            System.exit(1);
        }
    }
}
