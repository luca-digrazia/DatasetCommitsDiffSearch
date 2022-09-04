/*
 * Copyright 2012-2014 TORCH GmbH
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.graylog2.radio;

import com.beust.jcommander.JCommander;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.log4j.InstrumentedAppender;
import com.github.joschi.jadconfig.JadConfig;
import com.github.joschi.jadconfig.RepositoryException;
import com.github.joschi.jadconfig.ValidationException;
import com.github.joschi.jadconfig.repositories.PropertiesRepository;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Level;
import org.graylog2.inputs.gelf.http.GELFHttpInput;
import org.graylog2.inputs.gelf.tcp.GELFTCPInput;
import org.graylog2.inputs.gelf.udp.GELFUDPInput;
import org.graylog2.inputs.misc.jsonpath.JsonPathInput;
import org.graylog2.inputs.misc.metrics.LocalMetricsInput;
import org.graylog2.inputs.random.FakeHttpMessageInput;
import org.graylog2.inputs.raw.tcp.RawTCPInput;
import org.graylog2.inputs.raw.udp.RawUDPInput;
import org.graylog2.inputs.syslog.tcp.SyslogTCPInput;
import org.graylog2.inputs.syslog.udp.SyslogUDPInput;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.lifecycles.Lifecycle;
import org.graylog2.radio.bindings.RadioBindings;
import org.graylog2.shared.NodeRunner;
import org.graylog2.shared.ServerStatus;
import org.graylog2.shared.bindings.GuiceInstantiationService;
import org.graylog2.shared.inputs.InputRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.List;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class Main extends NodeRunner {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        final CommandLineArguments commandLineArguments = new CommandLineArguments();
        final JCommander jCommander = new JCommander(commandLineArguments, args);
        jCommander.setProgramName("graylog2-radio");

        if (commandLineArguments.isShowHelp()) {
            jCommander.usage();
            System.exit(0);
        }

        if (commandLineArguments.isShowVersion()) {
            System.out.println("Graylog2 Radio " + RadioVersion.VERSION);
            System.out.println("JRE: " + Tools.getSystemInformation());
            System.exit(0);
        }

        String configFile = commandLineArguments.getConfigFile();
        LOG.info("Using config file: {}", configFile);

        final Configuration configuration = new Configuration();
        JadConfig jadConfig = new JadConfig(new PropertiesRepository(configFile), configuration);

        LOG.info("Loading configuration");
        try {
            jadConfig.process();
        } catch (RepositoryException e) {
            LOG.error("Couldn't load configuration file: [{}]", configFile, e);
            System.exit(1);
        } catch (ValidationException e) {
            LOG.error("Invalid configuration", e);
            System.exit(1);
        }

        // Are we in debug mode?
        Level logLevel = Level.INFO;
        if (commandLineArguments.isDebug()) {
            LOG.info("Running in Debug mode");
            logLevel = Level.DEBUG;
        }

        GuiceInstantiationService instantiationService = new GuiceInstantiationService();
        List<Module> bindingsModules = getBindingsModules(instantiationService, new RadioBindings(configuration));
        Injector injector = Guice.createInjector(bindingsModules);
        instantiationService.setInjector(injector);

        // This is holding all our metrics.
        final MetricRegistry metrics = injector.getInstance(MetricRegistry.class);

        // Report metrics via JMX.
        final JmxReporter reporter = JmxReporter.forRegistry(metrics).build();
        reporter.start();

        InstrumentedAppender logMetrics = new InstrumentedAppender(metrics);
        logMetrics.activateOptions();
        org.apache.log4j.Logger.getRootLogger().setLevel(logLevel);
        org.apache.log4j.Logger.getLogger(Main.class.getPackage().getName()).setLevel(logLevel);
        org.apache.log4j.Logger.getRootLogger().addAppender(logMetrics);

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        LOG.info("Graylog2 Radio {} starting up. (JRE: {})", RadioVersion.VERSION, Tools.getSystemInformation());

        // Do not use a PID file if the user requested not to
        if (!commandLineArguments.isNoPidFile()) {
            savePidFile(commandLineArguments.getPidFile());
        }

        ServerStatus serverStatus = injector.getInstance(ServerStatus.class);

        // Register inputs. (find an automatic way here (annotations?) and do the same in graylog2-server.Main
        final InputRegistry inputRegistry = injector.getInstance(InputRegistry.class);
        inputRegistry.register(SyslogUDPInput.class, SyslogUDPInput.NAME);
        inputRegistry.register(SyslogTCPInput.class, SyslogTCPInput.NAME);
        inputRegistry.register(RawUDPInput.class, RawUDPInput.NAME);
        inputRegistry.register(RawTCPInput.class, RawTCPInput.NAME);
        inputRegistry.register(GELFUDPInput.class, GELFUDPInput.NAME);
        inputRegistry.register(GELFTCPInput.class, GELFTCPInput.NAME);
        inputRegistry.register(GELFHttpInput.class, GELFHttpInput.NAME);
        inputRegistry.register(FakeHttpMessageInput.class, FakeHttpMessageInput.NAME);
        inputRegistry.register(LocalMetricsInput.class, LocalMetricsInput.NAME);
        inputRegistry.register(JsonPathInput.class, JsonPathInput.NAME);

        monkeyPatchHK2(injector);

        Radio radio = injector.getInstance(Radio.class);
        serverStatus.setLifecycle(Lifecycle.STARTING);
        radio.initialize();

        // Register in Graylog2 cluster.
        //radio.ping();

        // Start regular pinging Graylog2 cluster to show that we are alive.
        //radio.startPings();


        LOG.info("Graylog2 Radio up and running.");

        while (true) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { /* lol, i don't care */ }
        }
    }

    private static void savePidFile(String pidFile) {

        String pid = Tools.getPID();
        Writer pidFileWriter = null;

        try {
            if (pid == null || pid.isEmpty() || pid.equals("unknown")) {
                throw new Exception("Could not determine PID.");
            }

            pidFileWriter = new FileWriter(pidFile);
            IOUtils.write(pid, pidFileWriter);
        } catch (Exception e) {
            LOG.error("Could not write PID file: " + e.getMessage(), e);
            System.exit(1);
        } finally {
            IOUtils.closeQuietly(pidFileWriter);
            // make sure to remove our pid when we exit
            new File(pidFile).deleteOnExit();
        }
    }

}
