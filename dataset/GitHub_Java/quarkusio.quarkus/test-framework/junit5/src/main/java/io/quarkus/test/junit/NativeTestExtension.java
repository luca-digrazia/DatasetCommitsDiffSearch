package io.quarkus.test.junit;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.enterprise.inject.Alternative;
import javax.inject.Inject;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.JUnitException;
import org.opentest4j.TestAbortedException;

import io.quarkus.runtime.configuration.ProfileManager;
import io.quarkus.runtime.test.TestHttpEndpointProvider;
import io.quarkus.test.common.NativeImageLauncher;
import io.quarkus.test.common.PropertyTestUtil;
import io.quarkus.test.common.RestAssuredURLManager;
import io.quarkus.test.common.TestResourceManager;
import io.quarkus.test.common.TestScopeManager;
import io.quarkus.test.common.http.TestHTTPResourceManager;

public class NativeTestExtension
        implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, TestInstancePostProcessor {

    private static boolean failedBoot;

    private static List<Function<Class<?>, String>> testHttpEndpointProviders;
    private static boolean ssl;

    private static Class<? extends QuarkusTestProfile> quarkusTestProfile;
    private static Throwable firstException; //if this is set then it will be thrown from the very first test that is run, the rest are aborted

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (!failedBoot) {
            RestAssuredURLManager.clearURL();
            TestScopeManager.tearDown(true);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (failedBoot) {
            throwBootFailureException();
        } else {
            RestAssuredURLManager.setURL(ssl, QuarkusTestExtension.getEndpointPath(context, testHttpEndpointProviders));
            TestScopeManager.setup(true);
        }
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        ensureStarted(extensionContext);
    }

    private void ensureNoInjectAnnotationIsUsed(Class<?> testClass) {
        Class<?> current = testClass;
        while (current.getSuperclass() != null) {
            for (Field field : current.getDeclaredFields()) {
                Inject injectAnnotation = field.getAnnotation(Inject.class);
                if (injectAnnotation != null) {
                    throw new JUnitException(
                            "@Inject is not supported in NativeImageTest tests. Offending field is "
                                    + field.getDeclaringClass().getTypeName() + "."
                                    + field.getName());
                }
            }
            current = current.getSuperclass();
        }

    }

    private ExtensionState ensureStarted(ExtensionContext extensionContext) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        ensureNoInjectAnnotationIsUsed(testClass);

        ExtensionContext root = extensionContext.getRoot();
        ExtensionContext.Store store = root.getStore(ExtensionContext.Namespace.GLOBAL);
        ExtensionState state = store.get(ExtensionState.class.getName(), ExtensionState.class);
        TestProfile annotation = testClass.getAnnotation(TestProfile.class);
        Class<? extends QuarkusTestProfile> selectedProfile = null;
        if (annotation != null) {
            selectedProfile = annotation.value();
        }
        boolean wrongProfile = !Objects.equals(selectedProfile, quarkusTestProfile);
        if ((state == null && !failedBoot) || wrongProfile) {
            if (wrongProfile) {
                if (state != null) {
                    try {
                        state.close();
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                }
            }
            PropertyTestUtil.setLogFileProperty();
            try {
                state = doNativeStart(extensionContext, selectedProfile);
                store.put(ExtensionState.class.getName(), state);

            } catch (Throwable e) {
                failedBoot = true;
                firstException = e;
            }
        }
        return state;
    }

    private ExtensionState doNativeStart(ExtensionContext context, Class<? extends QuarkusTestProfile> profile)
            throws Throwable {
        quarkusTestProfile = profile;
        TestResourceManager testResourceManager = null;
        try {
            Class<?> requiredTestClass = context.getRequiredTestClass();

            Map<String, String> sysPropRestore = new HashMap<>();
            sysPropRestore.put(ProfileManager.QUARKUS_TEST_PROFILE_PROP,
                    System.getProperty(ProfileManager.QUARKUS_TEST_PROFILE_PROP));

            QuarkusTestProfile profileInstance = null;
            final Map<String, String> additional = new HashMap<>();
            if (profile != null) {
                profileInstance = profile.newInstance();
                additional.putAll(profileInstance.getConfigOverrides());
                final Set<Class<?>> enabledAlternatives = profileInstance.getEnabledAlternatives();
                if (!enabledAlternatives.isEmpty()) {
                    additional.put("quarkus.arc.selected-alternatives", enabledAlternatives.stream()
                            .peek((c) -> {
                                if (!c.isAnnotationPresent(Alternative.class)) {
                                    throw new RuntimeException(
                                            "Enabled alternative " + c + " is not annotated with @Alternative");
                                }
                            })
                            .map(Class::getName).collect(Collectors.joining(",")));
                }
                final String configProfile = profileInstance.getConfigProfile();
                if (configProfile != null) {
                    additional.put(ProfileManager.QUARKUS_PROFILE_PROP, configProfile);
                }
                additional.put("quarkus.configuration.build-time-mismatch-at-runtime", "fail");
                for (Map.Entry<String, String> i : additional.entrySet()) {
                    sysPropRestore.put(i.getKey(), System.getProperty(i.getKey()));
                }
                for (Map.Entry<String, String> i : additional.entrySet()) {
                    System.setProperty(i.getKey(), i.getValue());
                }
            }

            testResourceManager = new TestResourceManager(requiredTestClass);
            testResourceManager.init();
            additional.putAll(testResourceManager.start());

            NativeImageLauncher launcher = new NativeImageLauncher(requiredTestClass);
            launcher.addSystemProperties(additional);
            try {
                launcher.start();
            } catch (IOException e) {
                try {
                    launcher.close();
                } catch (Throwable t) {
                }
                throw e;
            }
            if (launcher.isDefaultSsl()) {
                ssl = true;
            }

            final ExtensionState state = new ExtensionState(testResourceManager, launcher, sysPropRestore);

            testHttpEndpointProviders = TestHttpEndpointProvider.load();

            return state;
        } catch (Throwable e) {

            try {
                if (testResourceManager != null) {
                    testResourceManager.close();
                }
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            throw e;
        }
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        if (!failedBoot) {
            TestHTTPResourceManager.inject(testInstance);
            ExtensionContext root = context.getRoot();
            ExtensionContext.Store store = root.getStore(ExtensionContext.Namespace.GLOBAL);
            ExtensionState state = store.get(ExtensionState.class.getName(), ExtensionState.class);
            state.testResourceManager.inject(testInstance);
        }
    }

    private void throwBootFailureException() throws Exception {
        if (firstException != null) {
            Throwable throwable = firstException;
            firstException = null;
            throw new RuntimeException(throwable);
        } else {
            throw new TestAbortedException("Boot failed");
        }
    }

    public class ExtensionState implements ExtensionContext.Store.CloseableResource {

        private final TestResourceManager testResourceManager;
        private final Closeable resource;
        private final Map<String, String> sysPropRestore;
        private final Thread shutdownHook;

        ExtensionState(TestResourceManager testResourceManager, Closeable resource, Map<String, String> sysPropRestore) {
            this.testResourceManager = testResourceManager;
            this.resource = resource;
            this.sysPropRestore = sysPropRestore;
            this.shutdownHook = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ExtensionState.this.close();
                    } catch (IOException ignored) {
                    }
                }
            }, "Quarkus Test Cleanup Shutdown task");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

        }

        @Override
        public void close() throws IOException {
            testResourceManager.close();
            resource.close();
            for (Map.Entry<String, String> entry : sysPropRestore.entrySet()) {
                String val = entry.getValue();
                if (val == null) {
                    System.clearProperty(entry.getKey());
                } else {
                    System.setProperty(entry.getKey(), val);
                }
            }
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }
    }
}
