package io.quarkus.test.common;

import java.io.Closeable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

public class TestResourceManager implements Closeable {

    private final List<TestResourceEntry> testResourceEntries;
    private Map<String, String> oldSystemProps;

    public TestResourceManager(Class<?> testClass) {
        testResourceEntries = getTestResources(testClass);
    }

    public void init() {
        for (TestResourceEntry entry : testResourceEntries) {
            try {
                entry.getTestResource().init(entry.getArgs());
            } catch (Exception e) {
                throw new RuntimeException("Unable initialize test resource " + entry.getTestResource(), e);
            }
        }
    }

    public Map<String, String> start() {
        Map<String, String> ret = new HashMap<>();
        for (TestResourceEntry entry : testResourceEntries) {
            try {
                Map<String, String> start = entry.getTestResource().start();
                if (start != null) {
                    ret.putAll(start);
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to start Quarkus test resource " + entry.getTestResource(), e);
            }
        }
        oldSystemProps = new HashMap<>();
        for (Map.Entry<String, String> i : ret.entrySet()) {
            oldSystemProps.put(i.getKey(), System.getProperty(i.getKey()));
            if (i.getValue() == null) {
                System.clearProperty(i.getKey());
            } else {
                System.setProperty(i.getKey(), i.getValue());
            }
        }
        return ret;
    }

    public void inject(Object testInstance) {
        for (TestResourceEntry entry : testResourceEntries) {
            entry.getTestResource().inject(testInstance);
        }
    }

    public void close() {
        if (oldSystemProps != null) {
            for (Map.Entry<String, String> e : oldSystemProps.entrySet()) {
                if (e.getValue() == null) {
                    System.clearProperty(e.getKey());
                } else {
                    System.setProperty(e.getKey(), e.getValue());
                }

            }
        }
        oldSystemProps = null;
        for (TestResourceEntry entry : testResourceEntries) {
            try {
                entry.getTestResource().stop();
            } catch (Exception e) {
                throw new RuntimeException("Unable to stop Quarkus test resource " + entry.getTestResource(), e);
            }
        }
        try {
            ConfigProviderResolver cpr = ConfigProviderResolver.instance();
            cpr.releaseConfig(cpr.getConfig());
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private List<TestResourceEntry> getTestResources(Class<?> testClass) {
        IndexView index = TestClassIndexer.readIndex(testClass);

        List<TestResourceEntry> testResourceEntries = new ArrayList<>();

        // we need to keep track of duplicate entries to make sure we don't start the same resource
        // multiple times even if there are multiple same @QuarkusTestResource annotations
        Set<TestResourceClassEntry> alreadyAddedEntries = new HashSet<>();
        for (AnnotationInstance annotation : findQuarkusTestResourceInstances(index)) {
            try {
                Class<? extends QuarkusTestResourceLifecycleManager> testResourceClass = (Class<? extends QuarkusTestResourceLifecycleManager>) Class
                        .forName(annotation.value().asString(), true, Thread.currentThread().getContextClassLoader());

                AnnotationValue argsAnnotationValue = annotation.value("initArgs");
                Map<String, String> args;
                if (argsAnnotationValue == null) {
                    args = Collections.emptyMap();
                } else {
                    args = new HashMap<>();
                    AnnotationInstance[] resourceArgsInstances = argsAnnotationValue.asNestedArray();
                    for (AnnotationInstance resourceArgsInstance : resourceArgsInstances) {
                        args.put(resourceArgsInstance.value("name").asString(), resourceArgsInstance.value().asString());
                    }
                }

                TestResourceClassEntry testResourceClassEntry = new TestResourceClassEntry(testResourceClass, args);
                if (alreadyAddedEntries.contains(testResourceClassEntry)) {
                    continue;
                }
                alreadyAddedEntries.add(testResourceClassEntry);

                testResourceEntries.add(new TestResourceEntry(testResourceClass.getConstructor().newInstance(), args));
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                throw new RuntimeException("Unable to instantiate the test resource " + annotation.value().asString());
            }
        }

        for (QuarkusTestResourceLifecycleManager quarkusTestResourceLifecycleManager : ServiceLoader
                .load(QuarkusTestResourceLifecycleManager.class, Thread.currentThread().getContextClassLoader())) {
            testResourceEntries.add(new TestResourceEntry(quarkusTestResourceLifecycleManager));
        }

        testResourceEntries.sort(new Comparator<TestResourceEntry>() {

            private final QuarkusTestResourceLifecycleManagerComparator lifecycleManagerComparator = new QuarkusTestResourceLifecycleManagerComparator();

            @Override
            public int compare(TestResourceEntry o1, TestResourceEntry o2) {
                return lifecycleManagerComparator.compare(o1.getTestResource(), o2.getTestResource());
            }
        });

        return testResourceEntries;
    }

    private Collection<AnnotationInstance> findQuarkusTestResourceInstances(IndexView index) {
        Set<AnnotationInstance> testResourceAnnotations = new HashSet<>(index
                .getAnnotations(DotName.createSimple(QuarkusTestResource.class.getName())));
        for (AnnotationInstance annotation : index
                .getAnnotations(DotName.createSimple(QuarkusTestResource.List.class.getName()))) {
            Collections.addAll(testResourceAnnotations, annotation.value().asNestedArray());
        }
        return testResourceAnnotations;
    }

    private static class TestResourceEntry {
        private final QuarkusTestResourceLifecycleManager testResource;
        private final Map<String, String> args;

        public TestResourceEntry(QuarkusTestResourceLifecycleManager testResource) {
            this(testResource, Collections.emptyMap());
        }

        public TestResourceEntry(QuarkusTestResourceLifecycleManager testResource, Map<String, String> args) {
            this.testResource = testResource;
            this.args = args;
        }

        public QuarkusTestResourceLifecycleManager getTestResource() {
            return testResource;
        }

        public Map<String, String> getArgs() {
            return args;
        }
    }

    private static class TestResourceClassEntry {
        private Class<? extends QuarkusTestResourceLifecycleManager> clazz;
        private Map<String, String> args;

        public TestResourceClassEntry(Class<? extends QuarkusTestResourceLifecycleManager> clazz, Map<String, String> args) {
            this.clazz = clazz;
            this.args = args;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            TestResourceClassEntry that = (TestResourceClassEntry) o;
            return clazz.equals(that.clazz) &&
                    args.equals(that.args);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, args);
        }
    }

}
