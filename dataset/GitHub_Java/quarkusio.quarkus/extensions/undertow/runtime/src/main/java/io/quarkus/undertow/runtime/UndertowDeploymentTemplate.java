/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.quarkus.undertow.runtime;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.jboss.logging.Logger;
import org.wildfly.common.net.Inet;

import io.quarkus.arc.ManagedContext;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.Timing;
import io.quarkus.runtime.annotations.Template;
import io.undertow.Undertow;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.resource.CachingResourceManager;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.server.session.SessionIdGenerator;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.ClassIntrospecter;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.api.ServletStackTraces;
import io.undertow.servlet.api.ThreadSetupHandler;
import io.undertow.servlet.handlers.DefaultServlet;
import io.undertow.servlet.handlers.ServletPathMatches;

/**
 * Provides the runtime methods to bootstrap Undertow. This class is present in the final uber-jar,
 * and is invoked from generated bytecode
 */
@Template
public class UndertowDeploymentTemplate {

    private static final Logger log = Logger.getLogger("io.quarkus.undertow");

    public static final HttpHandler ROOT_HANDLER = new HttpHandler() {
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            currentRoot.handleRequest(exchange);
        }
    };
    private static final String RESOURCES_PROP = "quarkus.undertow.resources";

    private static volatile Undertow undertow;
    private static volatile HttpHandler currentRoot = ResponseCodeHandler.HANDLE_404;

    public RuntimeValue<DeploymentInfo> createDeployment(String name, Set<String> knownFile, Set<String> knownDirectories,
            LaunchMode launchMode, ShutdownContext context) {
        DeploymentInfo d = new DeploymentInfo();
        d.setSessionIdGenerator(new QuarkusSessionIdGenerator());
        d.setClassLoader(getClass().getClassLoader());
        d.setDeploymentName(name);
        d.setContextPath("/");
        d.setEagerFilterInit(true);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = new ClassLoader() {
            };
        }
        d.setClassLoader(cl);
        //TODO: we need better handling of static resources
        String resourcesDir = System.getProperty(RESOURCES_PROP);
        ResourceManager resourceManager;
        if (resourcesDir == null) {
            resourceManager = new KnownPathResourceManager(knownFile, knownDirectories,
                    new ClassPathResourceManager(d.getClassLoader(), "META-INF/resources"));
        } else {
            resourceManager = new PathResourceManager(Paths.get(resourcesDir));
        }
        if (launchMode == LaunchMode.NORMAL) {
            //todo: cache configuration
            resourceManager = new CachingResourceManager(1000, 0, null, resourceManager, 2000);
        }
        d.setResourceManager(resourceManager);

        if (launchMode == LaunchMode.DEVELOPMENT || launchMode == LaunchMode.TEST) {
            d.setServletStackTraces(ServletStackTraces.LOCAL_ONLY);
        } else {
            d.setServletStackTraces(ServletStackTraces.NONE);
        }
        d.addWelcomePages("index.html", "index.htm");

        d.addServlet(new ServletInfo(ServletPathMatches.DEFAULT_SERVLET_NAME, DefaultServlet.class).setAsyncSupported(true));

        context.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                try {
                    d.getResourceManager().close();
                } catch (IOException e) {
                    log.error("Failed to close Servlet ResourceManager", e);
                }
            }
        });
        return new RuntimeValue<>(d);
    }

    public static SocketAddress getHttpAddress() {
        for (Undertow.ListenerInfo info : undertow.getListenerInfo()) {
            if (info.getProtcol().equals("http") && info.getSslContext() == null) {
                return info.getAddress();
            }
        }
        return null;
    }

    public RuntimeValue<ServletInfo> registerServlet(RuntimeValue<DeploymentInfo> deploymentInfo,
            String name,
            Class<?> servletClass,
            boolean asyncSupported,
            int loadOnStartup,
            BeanContainer beanContainer, Map<String, String> initParams,
            InstanceFactory<? extends Servlet> instanceFactory) throws Exception {

        InstanceFactory<? extends Servlet> factory = instanceFactory != null ? instanceFactory
                : new QuarkusInstanceFactory(beanContainer.instanceFactory(servletClass));
        ServletInfo servletInfo = new ServletInfo(name, (Class<? extends Servlet>) servletClass,
                factory);
        for (Map.Entry<String, String> e : initParams.entrySet()) {
            servletInfo.addInitParam(e.getKey(), e.getValue());
        }
        deploymentInfo.getValue().addServlet(servletInfo);
        servletInfo.setAsyncSupported(asyncSupported);
        if (loadOnStartup > 0) {
            servletInfo.setLoadOnStartup(loadOnStartup);
        }
        return new RuntimeValue<>(servletInfo);
    }

    public void addServletInitParam(RuntimeValue<ServletInfo> info, String name, String value) {
        info.getValue().addInitParam(name, value);
    }

    public void addServletMapping(RuntimeValue<DeploymentInfo> info, String name, String mapping) throws Exception {
        ServletInfo sv = info.getValue().getServlets().get(name);
        sv.addMapping(mapping);
    }

    public void setMultipartConfig(RuntimeValue<ServletInfo> sref, String location, long fileSize, long maxRequestSize,
            int fileSizeThreshold) {
        MultipartConfigElement mp = new MultipartConfigElement(location, fileSize, maxRequestSize, fileSizeThreshold);
        sref.getValue().setMultipartConfig(mp);
    }

    /**
     * @param sref
     * @param securityInfo
     */
    public void setSecurityInfo(RuntimeValue<ServletInfo> sref, ServletSecurityInfo securityInfo) {
        sref.getValue().setServletSecurityInfo(securityInfo);
    }

    /**
     * @param sref
     * @param roleName
     * @param roleLink
     */
    public void addSecurityRoleRef(RuntimeValue<ServletInfo> sref, String roleName, String roleLink) {
        sref.getValue().addSecurityRoleRef(roleName, roleLink);
    }

    public RuntimeValue<FilterInfo> registerFilter(RuntimeValue<DeploymentInfo> info,
            String name, Class<?> filterClass,
            boolean asyncSupported,
            BeanContainer beanContainer,
            Map<String, String> initParams,
            InstanceFactory<? extends Filter> instanceFactory) throws Exception {

        InstanceFactory<? extends Filter> factory = instanceFactory != null ? instanceFactory
                : new QuarkusInstanceFactory(beanContainer.instanceFactory(filterClass));
        FilterInfo filterInfo = new FilterInfo(name, (Class<? extends Filter>) filterClass, factory);

        for (Map.Entry<String, String> e : initParams.entrySet()) {
            filterInfo.addInitParam(e.getKey(), e.getValue());
        }
        info.getValue().addFilter(filterInfo);
        filterInfo.setAsyncSupported(asyncSupported);
        return new RuntimeValue<>(filterInfo);
    }

    public void addFilterInitParam(RuntimeValue<FilterInfo> info, String name, String value) {
        info.getValue().addInitParam(name, value);
    }

    public void addFilterURLMapping(RuntimeValue<DeploymentInfo> info, String name, String mapping,
            DispatcherType dispatcherType) throws Exception {
        info.getValue().addFilterUrlMapping(name, mapping, dispatcherType);
    }

    public void addFilterServletNameMapping(RuntimeValue<DeploymentInfo> info, String name, String mapping,
            DispatcherType dispatcherType) throws Exception {
        info.getValue().addFilterServletNameMapping(name, mapping, dispatcherType);
    }

    public void registerListener(RuntimeValue<DeploymentInfo> info, Class<?> listenerClass, BeanContainer factory) {
        info.getValue()
                .addListener(new ListenerInfo((Class<? extends EventListener>) listenerClass,
                        (InstanceFactory<? extends EventListener>) new QuarkusInstanceFactory<>(
                                factory.instanceFactory(listenerClass))));
    }

    public void addServltInitParameter(RuntimeValue<DeploymentInfo> info, String name, String value) {
        info.getValue().addInitParameter(name, value);
    }

    public RuntimeValue<Undertow> startUndertow(ShutdownContext shutdown, DeploymentManager manager, HttpConfig config,
            List<HandlerWrapper> wrappers, LaunchMode launchMode) throws ServletException {

        if (undertow == null) {
            startUndertowEagerly(config, null, launchMode);

            //in development mode undertow is started eagerly
            shutdown.addShutdownTask(new Runnable() {
                @Override
                public void run() {
                    undertow.stop();
                    undertow = null;
                }
            });
        }
        shutdown.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                try {
                    manager.stop();
                } catch (ServletException e) {
                    log.error("Failed to stop deployment", e);
                }
                manager.undeploy();
            }
        });
        HttpHandler main = manager.getDeployment().getHandler();
        for (HandlerWrapper i : wrappers) {
            main = i.wrap(main);
        }
        currentRoot = main;

        Timing.setHttpServer(String.format(
                "Listening on: " + undertow.getListenerInfo().stream().map(l -> {
                    String address;
                    if (l.getAddress() instanceof InetSocketAddress) {
                        InetSocketAddress inetAddress = (InetSocketAddress) l.getAddress();
                        address = Inet.toURLString(inetAddress.getAddress(), true) + ":" + inetAddress.getPort();
                    } else {
                        address = l.getAddress().toString();
                    }
                    return l.getProtcol() + "://" + address;
                }).collect(Collectors.joining(", "))));

        return new RuntimeValue<>(undertow);
    }

    /**
     * Used for quarkus:run, where we want undertow to start very early in the process.
     * <p>
     * This enables recovery from errors on boot. In a normal boot undertow is one of the last things start, so there would
     * be no chance to use hot deployment to fix the error. In development mode we start Undertow early, so any error
     * on boot can be corrected via the hot deployment handler
     */
    public static void startUndertowEagerly(HttpConfig config, HandlerWrapper hotDeploymentWrapper, LaunchMode launchMode)
            throws ServletException {
        if (undertow == null) {
            int port = config.determinePort(launchMode);
            log.debugf("Starting Undertow on port %d", port);
            HttpHandler rootHandler = new CanonicalPathHandler(ROOT_HANDLER);
            if (hotDeploymentWrapper != null) {
                rootHandler = hotDeploymentWrapper.wrap(rootHandler);
            }

            Undertow.Builder builder = Undertow.builder()
                    .addHttpListener(port, config.host)
                    .setHandler(rootHandler);
            if (config.ioThreads.isPresent()) {
                builder.setIoThreads(config.ioThreads.getAsInt());
            } else if (launchMode.isDevOrTest()) {
                //we limit the number of IO and worker threads in development and testing mode
                builder.setIoThreads(2);
            }
            if (config.workerThreads.isPresent()) {
                builder.setWorkerThreads(config.workerThreads.getAsInt());
            } else if (launchMode.isDevOrTest()) {
                builder.setWorkerThreads(6);
            }
            undertow = builder
                    .build();
            undertow.start();
        }
    }

    public DeploymentManager bootServletContainer(RuntimeValue<DeploymentInfo> info, BeanContainer beanContainer) {
        try {
            ClassIntrospecter defaultVal = info.getValue().getClassIntrospecter();
            info.getValue().setClassIntrospecter(new ClassIntrospecter() {
                @Override
                public <T> InstanceFactory<T> createInstanceFactory(Class<T> clazz) throws NoSuchMethodException {
                    BeanContainer.Factory<T> res = beanContainer.instanceFactory(clazz);
                    if (res == null) {
                        return defaultVal.createInstanceFactory(clazz);
                    }
                    return new InstanceFactory<T>() {
                        @Override
                        public InstanceHandle<T> createInstance() throws InstantiationException {
                            BeanContainer.Instance<T> ih = res.create();
                            return new InstanceHandle<T>() {
                                @Override
                                public T getInstance() {
                                    return ih.get();
                                }

                                @Override
                                public void release() {
                                    ih.close();
                                }
                            };
                        }
                    };
                }
            });
            ServletContainer servletContainer = Servlets.defaultContainer();
            DeploymentManager manager = servletContainer.addDeployment(info.getValue());
            manager.deploy();
            manager.start();
            return manager;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addServletContextAttribute(RuntimeValue<DeploymentInfo> deployment, String key, Object value1) {
        deployment.getValue().addServletContextAttribute(key, value1);
    }

    public void addServletExtension(RuntimeValue<DeploymentInfo> deployment, ServletExtension extension) {
        deployment.getValue().addServletExtension(extension);
    }

    public ServletExtension setupRequestScope(BeanContainer beanContainer) {
        return new ServletExtension() {
            @Override
            public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
                deploymentInfo.addThreadSetupAction(new ThreadSetupHandler() {
                    @Override
                    public <T, C> ThreadSetupHandler.Action<T, C> create(Action<T, C> action) {
                        return new Action<T, C>() {
                            @Override
                            public T call(HttpServerExchange exchange, C context) throws Exception {
                                ManagedContext requestContext = beanContainer.requestContext();
                                if (requestContext.isActive()) {
                                    return action.call(exchange, context);
                                } else {
                                    try {
                                        requestContext.activate();
                                        return action.call(exchange, context);
                                    } finally {
                                        requestContext.terminate();
                                    }
                                }
                            }
                        };
                    }
                });
            }
        };
    }

    /**
     * we can't have SecureRandom in the native image heap, so we need to lazy init
     */
    private static class QuarkusSessionIdGenerator implements SessionIdGenerator {

        private volatile SecureRandom random;

        private volatile int length = 30;

        private static final char[] SESSION_ID_ALPHABET;

        private static final String ALPHABET_PROPERTY = "io.undertow.server.session.SecureRandomSessionIdGenerator.ALPHABET";

        static {
            String alphabet = System.getProperty(ALPHABET_PROPERTY,
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_");
            if (alphabet.length() != 64) {
                throw new RuntimeException(
                        "io.undertow.server.session.SecureRandomSessionIdGenerator must be exactly 64 characters long");
            }
            SESSION_ID_ALPHABET = alphabet.toCharArray();
        }

        @Override
        public String createSessionId() {
            if (random == null) {
                random = new SecureRandom();
            }
            final byte[] bytes = new byte[length];
            random.nextBytes(bytes);
            return new String(encode(bytes));
        }

        public int getLength() {
            return length;
        }

        public void setLength(final int length) {
            this.length = length;
        }

        /**
         * Encode the bytes into a String with a slightly modified Base64-algorithm
         * This code was written by Kevin Kelley <kelley@ruralnet.net>
         * and adapted by Thomas Peuss <jboss@peuss.de>
         *
         * @param data The bytes you want to encode
         * @return the encoded String
         */
        private char[] encode(byte[] data) {
            char[] out = new char[((data.length + 2) / 3) * 4];
            char[] alphabet = SESSION_ID_ALPHABET;
            //
            // 3 bytes encode to 4 chars.  Output is always an even
            // multiple of 4 characters.
            //
            for (int i = 0, index = 0; i < data.length; i += 3, index += 4) {
                boolean quad = false;
                boolean trip = false;

                int val = (0xFF & (int) data[i]);
                val <<= 8;
                if ((i + 1) < data.length) {
                    val |= (0xFF & (int) data[i + 1]);
                    trip = true;
                }
                val <<= 8;
                if ((i + 2) < data.length) {
                    val |= (0xFF & (int) data[i + 2]);
                    quad = true;
                }
                out[index + 3] = alphabet[(quad ? (val & 0x3F) : 63)];
                val >>= 6;
                out[index + 2] = alphabet[(trip ? (val & 0x3F) : 63)];
                val >>= 6;
                out[index + 1] = alphabet[val & 0x3F];
                val >>= 6;
                out[index] = alphabet[val & 0x3F];
            }
            return out;
        }
    }
}
