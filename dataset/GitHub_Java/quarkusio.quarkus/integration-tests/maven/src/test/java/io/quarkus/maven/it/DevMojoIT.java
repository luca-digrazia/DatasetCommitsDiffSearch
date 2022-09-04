package io.quarkus.maven.it;

import static io.quarkus.maven.it.ApplicationNameAndVersionTestUtil.assertApplicationPropertiesSetCorrectly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.junit.jupiter.api.Test;

import io.quarkus.maven.it.verifier.MavenProcessInvocationResult;
import io.quarkus.maven.it.verifier.RunningInvoker;
import io.quarkus.test.devmode.util.DevModeTestUtils;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 *
 *         NOTE to anyone diagnosing failures in this test, to run a single method use:
 *
 *         mvn install -Dit.test=DevMojoIT#methodName
 */
@DisableForNative
public class DevMojoIT extends RunAndCheckMojoTestBase {

    @Test
    public void testPropertyOverridesTest() throws MavenInvocationException, IOException {
        testDir = getTargetDir("projects/property-overrides");
        runAndCheck("-Dlocal-dep.version=1.0-SNAPSHOT");
    }

    @Test
    public void testThatClassAppCanRun() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic", "projects/project-classic-run");
        runAndCheck();

        //make sure that the Class.getPackage() works for app classes
        String pkg = DevModeTestUtils.getHttpResponse("/app/hello/package");
        assertThat(pkg).isEqualTo("org.acme");

        //make sure the proper profile is set
        String profile = DevModeTestUtils.getHttpResponse("/app/hello/profile");
        assertThat(profile).isEqualTo("dev");

        //make sure webjars work
        DevModeTestUtils.getHttpResponse("webjars/bootstrap/3.1.0/css/bootstrap.min.css");

        assertThatOutputWorksCorrectly(running.log());

        assertApplicationPropertiesSetCorrectly();
    }

    @Test
    public void testThatResteasyWithoutUndertowCanRun() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic-no-undertow", "projects/project-classic-no-undertow-run");
        run(false);

        //make sure that a simple HTTP GET request always works
        IntStream.range(0, 10).forEach(i -> {
            assertThat(DevModeTestUtils.getStrictHttpResponse("/hello", 200)).isTrue();
        });
    }

    @Test
    public void testThatInitialMavenResourceFilteringWorks() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic-resource-filtering", "projects/project-classic-resource-filtering");

        //also test that a zipfile must not be filtered because of nonFilteredFileExtensions configuration
        //as initProject() would already corrupt the zipfile, it has to be created _after_ initProject()
        try (ZipOutputStream zipOut = new ZipOutputStream(
                new FileOutputStream(new File(testDir, "src/main/resources/test.zip")))) {
            ZipEntry zipEntry = new ZipEntry("test.txt");
            zipOut.putNextEntry(zipEntry);
            zipOut.write("test".getBytes());
        }

        run(false);

        //make sure that a simple HTTP GET request always works
        IntStream.range(0, 10).forEach(i -> {
            assertThat(DevModeTestUtils.getStrictHttpResponse("/hello", 200)).isTrue();
        });

        //try to open the copied test.zip (which will fail if it was filtered)
        File copiedTestZipFile = new File(testDir, "target/classes/test.zip");
        assertThat(copiedTestZipFile).exists();
        try (ZipFile zipFile = new ZipFile(copiedTestZipFile)) {
            //everything is fine once we get here (ZipFile is still readable)
        }
    }

    @Test
    public void testThatTheApplicationIsReloadedOnJavaChange() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic", "projects/project-classic-run-java-change");
        runAndCheck();

        // Edit the "Hello" message.
        File source = new File(testDir, "src/main/java/org/acme/HelloResource.java");
        String uuid = UUID.randomUUID().toString();
        filter(source, Collections.singletonMap("return \"hello\";", "return \"" + uuid + "\";"));

        // Wait until we get "uuid"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> DevModeTestUtils.getHttpResponse("/app/hello").contains(uuid));

        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(source::isFile);

        filter(source, Collections.singletonMap(uuid, "carambar"));

        // Wait until we get "carambar"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> DevModeTestUtils.getHttpResponse("/app/hello").contains("carambar"));
    }

    @Test
    public void testThatSourceChangesAreDetectedOnPomChange() throws Exception {
        testDir = initProject("projects/classic", "projects/project-classic-run-src-and-pom-change");
        runAndCheck();

        // Edit a Java file too
        final File javaSource = new File(testDir, "src/main/java/org/acme/HelloResource.java");
        final String uuid = UUID.randomUUID().toString();
        filter(javaSource, Collections.singletonMap("return \"hello\";", "return \"hello " + uuid + "\";"));

        // edit the application.properties too
        final File applicationProps = new File(testDir, "src/main/resources/application.properties");
        filter(applicationProps, Collections.singletonMap("greeting=bonjour", "greeting=" + uuid + ""));

        // Now edit the pom.xml to trigger the dev mode restart
        final File pomSource = new File(testDir, "pom.xml");
        filter(pomSource, Collections.singletonMap("<!-- insert test dependencies here -->",
                "        <dependency>\n" +
                        "            <groupId>io.quarkus</groupId>\n" +
                        "            <artifactId>quarkus-smallrye-openapi</artifactId>\n" +
                        "        </dependency>"));

        // Wait until we get the updated responses
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/hello").contains("hello " + uuid));

        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/hello/greeting").contains(uuid));

    }

    @Test
    public void testAlternatePom() throws Exception {
        testDir = initProject("projects/classic", "projects/project-classic-alternate-pom");

        File pom = new File(testDir, "pom.xml");
        if (!pom.exists()) {
            throw new IllegalStateException("Failed to locate project's pom.xml at " + pom);
        }
        final String alternatePomName = "alternate-pom.xml";
        File alternatePom = new File(testDir, alternatePomName);
        if (alternatePom.exists()) {
            alternatePom.delete();
        }
        pom.renameTo(alternatePom);
        if (pom.exists()) {
            throw new IllegalStateException(pom + " was expected to be renamed to " + alternatePom);
        }
        runAndCheck("-f", alternatePomName);

        // Edit a Java file too
        final File javaSource = new File(testDir, "src/main/java/org/acme/HelloResource.java");
        final String uuid = UUID.randomUUID().toString();
        filter(javaSource, Collections.singletonMap("return \"hello\";", "return \"hello " + uuid + "\";"));

        // edit the application.properties too
        final File applicationProps = new File(testDir, "src/main/resources/application.properties");
        filter(applicationProps, Collections.singletonMap("greeting=bonjour", "greeting=" + uuid + ""));

        // Now edit the pom.xml to trigger the dev mode restart
        filter(alternatePom, Collections.singletonMap("<!-- insert test dependencies here -->",
                "        <dependency>\n" +
                        "            <groupId>io.quarkus</groupId>\n" +
                        "            <artifactId>quarkus-smallrye-openapi</artifactId>\n" +
                        "        </dependency>"));

        // Wait until we get the updated responses
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/hello").contains("hello " + uuid));

        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/hello/greeting").contains(uuid));

    }

    @Test
    public void testThatTheApplicationIsReloadedOnPomChange() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic", "projects/project-classic-run-pom-change");
        runAndCheck();

        // Edit the pom.xml.
        File source = new File(testDir, "pom.xml");
        filter(source, Collections.singletonMap("<!-- insert test dependencies here -->",
                "        <dependency>\n" +
                        "            <groupId>io.quarkus</groupId>\n" +
                        "            <artifactId>quarkus-smallrye-openapi</artifactId>\n" +
                        "        </dependency>"));

        // Wait until we get "uuid"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> DevModeTestUtils.getHttpResponse("/openapi").contains("hello"));
    }

    @Test
    public void testProjectWithExtension() throws MavenInvocationException, IOException {
        testDir = getTargetDir("projects/project-with-extension");
        runAndCheck();

        final List<String> extDepWarnings = Files.readAllLines(testDir.toPath().resolve("build-project-with-extension.log"))
                .stream().filter(s -> s.startsWith("[WARNING] Local Quarkus extension dependency "))
                .collect(Collectors.toList());
        assertTrue(extDepWarnings
                .contains("[WARNING] Local Quarkus extension dependency org.acme:acme-quarkus-ext will not be hot-reloadable"));
        assertTrue(extDepWarnings
                .contains("[WARNING] Local Quarkus extension dependency org.acme:acme-common will not be hot-reloadable"));
        assertTrue(extDepWarnings.contains(
                "[WARNING] Local Quarkus extension dependency org.acme:acme-common-transitive will not be hot-reloadable"));
        assertEquals(3, extDepWarnings.size());
    }

    @Test
    public void testThatTheApplicationIsReloadedMultiModule() throws MavenInvocationException, IOException {
        testDir = initProject("projects/multimodule", "projects/multimodule-with-deps");
        runAndCheck();

        // test that we don't get multiple instances of a resource when loading from the ClassLoader
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/hello/resourcesCount").equals("1"));

        // Edit the "Hello" message.
        File source = new File(testDir, "rest/src/main/java/org/acme/HelloResource.java");
        final String uuid = UUID.randomUUID().toString();
        filter(source, Collections.singletonMap("return \"hello\";", "return \"" + uuid + "\";"));

        // Wait until we get "uuid"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> DevModeTestUtils.getHttpResponse("/app/hello").contains(uuid));

        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(source::isFile);

        filter(source, Collections.singletonMap(uuid, "carambar"));

        // Wait until we get "carambar"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> DevModeTestUtils.getHttpResponse("/app/hello").contains("carambar"));

        // Create a new resource
        source = new File(testDir, "html/src/main/resources/META-INF/resources/lorem.txt");
        FileUtils.write(source,
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
                "UTF-8");
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/lorem.txt").contains("Lorem ipsum"));

        // Update the resource
        FileUtils.write(source, uuid, "UTF-8");
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/lorem.txt").contains(uuid));

        // Delete the resource
        source.delete();
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/lorem.txt", 404));
    }

    @Test
    public void testMultiModuleDevModeWithLocalDepsDisabled() throws MavenInvocationException, IOException {
        testDir = initProject("projects/multimodule", "projects/multimodule-nodeps");
        runAndCheck("-DnoDeps");

        String greeting = DevModeTestUtils.getHttpResponse("/app/hello/greeting");
        assertThat(greeting).containsIgnoringCase("bonjour");

        // Edit the "Hello" message.
        File source = new File(testDir, "rest/src/main/java/org/acme/HelloResource.java");
        filter(source, Collections.singletonMap("return \"hello\";", "return \"" + UUID.randomUUID().toString() + "\";"));

        // Edit the greeting property.
        source = new File(testDir, "runner/src/main/resources/application.properties");
        final String uuid = UUID.randomUUID().toString();
        filter(source, Collections.singletonMap("greeting=bonjour", "greeting=" + uuid + ""));

        // Wait until we get "uuid"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/hello/greeting").contains(uuid));

        greeting = DevModeTestUtils.getHttpResponse("/app/hello");
        assertThat(greeting).containsIgnoringCase("hello");
    }

    @Test
    public void testMultiModuleProjectWithRevisionVersion() throws MavenInvocationException, IOException {
        testDir = initProject("projects/multimodule-revision-prop");
        final String projectVersion = System.getProperty("project.version");
        runAndCheck("-Dquarkus.platform.version=" + projectVersion,
                "-Dquarkus-plugin.version=" + projectVersion);

        // Edit the "Hello" message.
        File source = new File(testDir, "rest/src/main/java/org/acme/HelloResource.java");
        final String uuid = UUID.randomUUID().toString();
        filter(source, Collections.singletonMap("return \"hello\";", "return \"" + uuid + "\";"));

        // Wait until we get "uuid"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> DevModeTestUtils.getHttpResponse("/app/hello").contains(uuid));

        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(source::isFile);
    }

    @Test
    public void testTestScopedLocalProjectDependency() throws MavenInvocationException, IOException {
        testDir = initProject("projects/test-module-dependency");
        final String projectVersion = System.getProperty("project.version");
        run(true, "-Dquarkus.platform.version=" + projectVersion,
                "-Dquarkus-plugin.version=" + projectVersion);

        assertEquals("Test class is not visible", DevModeTestUtils.getHttpResponse("/hello"));
    }

    @Test
    public void testThatTheApplicationIsReloadedOnNewResource() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic", "projects/project-classic-run-new-resource");
        runAndCheck();

        File source = new File(testDir, "src/main/java/org/acme/MyNewResource.java");
        String myNewResource = "package org.acme;\n" +
                "\n" +
                "import javax.ws.rs.GET;\n" +
                "import javax.ws.rs.Path;\n" +
                "import javax.ws.rs.Produces;\n" +
                "import javax.ws.rs.core.MediaType;\n" +
                "\n" +
                "@Path(\"/foo\")\n" +
                "public class MyNewResource {\n" +

                "    @GET\n" +
                "    @Produces(MediaType.TEXT_PLAIN)\n" +
                "    public String foo() {\n" +
                "        return \"bar\";\n" +
                "    }\n" +
                "}\n";
        FileUtils.write(source, myNewResource, Charset.forName("UTF-8"));

        // Wait until we get "bar"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> DevModeTestUtils.getHttpResponse("/app/foo").contains("bar"));
    }

    @Test
    public void testThatClassFileAreCleanedUp() throws MavenInvocationException, IOException, InterruptedException {
        testDir = initProject("projects/classic", "projects/project-class-file-deletion");

        File source = new File(testDir, "src/main/java/org/acme/ClassDeletionResource.java");
        String classDeletionResource = "package org.acme;\n" +
                "\n" +
                "import javax.ws.rs.GET;\n" +
                "import javax.ws.rs.Path;\n" +
                "import javax.ws.rs.Produces;\n" +
                "import javax.ws.rs.core.MediaType;\n" +
                "\n" +
                "@Path(\"/deletion\")\n" +
                "public class ClassDeletionResource {\n" +
                "    public static class InnerClass {} \n" +
                "    @GET\n" +
                "    @Produces(MediaType.TEXT_PLAIN)\n" +
                "    public String toDelete() {\n" +
                "        return Hello.message();\n" +
                "    }\n" +
                "}\n " +
                "class Hello {\n" +
                "    public static String message() {\n" +
                "        return \"to be deleted\";\n" +
                "    }\n" +
                "}";
        FileUtils.write(source, classDeletionResource, Charset.forName("UTF-8"));

        runAndCheck();
        // Wait until source file is compiled
        await()
                .pollDelay(1, TimeUnit.SECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/deletion").contains("to be deleted"));

        // Remove InnerClass
        filter(source, Collections.singletonMap("public static class InnerClass {}", ""));

        File helloClassFile = new File(testDir, "target/classes/org/acme/Hello.class");
        File innerClassFile = new File(testDir, "target/classes/org/acme/ClassDeletionResource$InnerClass.class");
        File classDeletionResourceClassFile = new File(testDir, "target/classes/org/acme/ClassDeletionResource.class");

        // Make sure that other class files have not been deleted.
        await()
                .pollDelay(1, TimeUnit.SECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/hello/package", 200));

        // Verify that only ClassDeletionResource$InnerClass.class to be deleted
        assertThat(innerClassFile).doesNotExist();
        assertThat(helloClassFile).exists();
        assertThat(classDeletionResourceClassFile).exists();

        // Delete source file
        source.delete();

        // Wait until we get "404 Not Found" because ClassDeletionResource.class have been deleted.
        await()
                .pollDelay(1, TimeUnit.SECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/deletion", 404));

        // Make sure that class files for the deleted source file have also been deleted
        assertThat(helloClassFile).doesNotExist();
        assertThat(classDeletionResourceClassFile).doesNotExist();
    }

    @Test
    public void testSourceModificationBeforeFirstCallWorks() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic", "projects/project-classic-source-modification-before-first-call");
        run(true);

        File source = new File(testDir, "src/main/java/org/acme/HelloResource.java");
        // Edit the "Hello" message and provide a random string.
        String uuid = UUID.randomUUID().toString();
        filter(source, Collections.singletonMap("return \"hello\";", "return \"" + uuid + "\";"));

        // Check that the random string is returned
        String greeting = DevModeTestUtils.getHttpResponse("/app/hello");
        assertThat(greeting).containsIgnoringCase(uuid);
    }

    @Test
    public void testThatTheApplicationIsReloadedOnConfigChange() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic", "projects/project-classic-run-config-change");
        assertThat(testDir).isDirectory();
        running = new RunningInvoker(testDir, false);
        final Properties mvnRunProps = new Properties();
        mvnRunProps.setProperty("debug", "false");
        running.execute(Arrays.asList("compile", "quarkus:dev"), Collections.emptyMap(), mvnRunProps);

        String resp = DevModeTestUtils.getHttpResponse();

        assertThat(resp).containsIgnoringCase("ready").containsIgnoringCase("application").containsIgnoringCase("org.acme")
                .containsIgnoringCase("1.0-SNAPSHOT");

        String greeting = DevModeTestUtils.getHttpResponse("/app/hello/greeting");
        assertThat(greeting).containsIgnoringCase("bonjour");

        File source = new File(testDir, "src/main/resources/application.properties");
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(source::isFile);

        String uuid = UUID.randomUUID().toString();
        filter(source, Collections.singletonMap("bonjour", uuid));

        // Wait until we get "uuid"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/hello/greeting").contains(uuid));
    }

    @Test
    public void testThatAddingConfigFileWorksCorrectly() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic-noconfig", "projects/project-classic-run-noconfig-add-config");
        assertThat(testDir).isDirectory();
        running = new RunningInvoker(testDir, false);
        final Properties mvnRunProps = new Properties();
        mvnRunProps.setProperty("debug", "false");
        running.execute(Arrays.asList("compile", "quarkus:dev"), Collections.emptyMap(), mvnRunProps);

        String resp = DevModeTestUtils.getHttpResponse();

        assertThat(resp).containsIgnoringCase("ready").containsIgnoringCase("application").containsIgnoringCase("org.acme")
                .containsIgnoringCase("1.0-SNAPSHOT");

        String greeting = DevModeTestUtils.getHttpResponse("/app/hello/greeting");
        assertThat(greeting).contains("initialValue");

        File configurationFile = new File(testDir, "src/main/resources/application.properties");
        assertThat(configurationFile).doesNotExist();

        String uuid = UUID.randomUUID().toString();

        FileUtils.write(configurationFile,
                "greeting=" + uuid,
                "UTF-8");
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(configurationFile::isFile);

        // Wait until we get "uuid"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/hello/greeting").contains(uuid));
    }

    @Test
    public void testThatExternalConfigOverridesConfigInJar() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic", "projects/project-classic-external-config");
        File configurationFile = new File(testDir, "target/config/application.properties");
        assertThat(configurationFile).doesNotExist();

        String uuid = UUID.randomUUID().toString();

        FileUtils.write(configurationFile,
                "greeting=" + uuid,
                "UTF-8");
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(configurationFile::isFile);

        run(true);

        // Wait until we get "uuid"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(60, TimeUnit.SECONDS)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/hello/greeting").contains(uuid));
    }

    @Test
    public void testThatNewResourcesAreServed() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic", "projects/project-classic-run-resource-change");
        runAndCheck();

        // Create a new resource
        File source = new File(testDir, "src/main/resources/META-INF/resources/lorem.txt");
        FileUtils.write(source,
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
                "UTF-8");
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/lorem.txt").contains("Lorem ipsum"));

        // Update the resource
        String uuid = UUID.randomUUID().toString();
        FileUtils.write(source, uuid, "UTF-8");
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/lorem.txt").contains(uuid));

        // Delete the resource
        source.delete();
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/lorem.txt", 404));
    }

    @Test
    public void testThatApplicationRecoversCompilationIssue() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic", "projects/project-classic-run-compilation-issue");
        runAndCheck();

        // Edit the "Hello" message.
        File source = new File(testDir, "src/main/java/org/acme/HelloResource.java");
        String uuid = UUID.randomUUID().toString();
        filter(source, Collections.singletonMap("return \"hello\";", "return \"" + uuid + "\"")); // No semi-colon

        // Wait until we get "uuid"
        AtomicReference<String> last = new AtomicReference<>();
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> {
                    String content = DevModeTestUtils.getHttpResponse("/app/hello", true);
                    last.set(content);
                    return content.contains(uuid);
                });

        assertThat(last.get()).containsIgnoringCase("error")
                .containsIgnoringCase("return \"" + uuid + "\"")
                .containsIgnoringCase("compile");

        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(source::isFile);
        filter(source, Collections.singletonMap("\"" + uuid + "\"", "\"carambar\";"));

        // Wait until we get "uuid"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> DevModeTestUtils.getHttpResponse("/app/hello").contains("carambar"));
    }

    @Test
    public void testThatApplicationRecoversStartupIssue() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic", "projects/project-classic-run-startup-issue");

        // Edit the JAX-RS resource to be package private
        File source = new File(testDir, "src/main/java/org/acme/HelloResource.java");
        filter(source, Collections.singletonMap("public class HelloResource", "class HelloResource"));

        runAndExpectError();
        // Wait until we get the error page
        AtomicReference<String> last = new AtomicReference<>();
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> {
                    String content = DevModeTestUtils.getHttpResponse("/app/hello", true);
                    last.set(content);
                    return content.contains("Error restarting Quarkus");
                });

        assertThat(last.get()).containsIgnoringCase("Error restarting Quarkus");

        filter(source, Collections.singletonMap("class HelloResource", "public class HelloResource"));

        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> {
                    String content = DevModeTestUtils.getHttpResponse("/app/hello", true);
                    last.set(content);
                    return content.equals("hello");
                });
        assertThat(last.get()).isEqualTo("hello");
    }

    @Test
    public void testThatNewBeanAreDiscovered() throws IOException, MavenInvocationException {
        testDir = initProject("projects/classic", "projects/project-classic-run-new-bean");
        runAndCheck();

        // Edit the "Hello" message.
        File source = new File(testDir, "src/main/java/org/acme/MyBean.java");
        String content = "package org.acme;\n" +
                "\n" +
                "import javax.enterprise.context.ApplicationScoped;\n" +
                "\n" +
                "@ApplicationScoped\n" +
                "public class MyBean {\n" +
                "\n" +
                "    public String get() {\n" +
                "        return \"message\";\n" +
                "    }\n" +
                "    \n" +
                "}";
        FileUtils.write(source, content, "UTF-8");

        // Update the resource to use the bean
        File resource = new File(testDir, "src/main/java/org/acme/HelloResource.java");
        filter(resource, Collections.singletonMap("String greeting;", "String greeting;\n @Inject MyBean bean;"));
        filter(resource, Collections.singletonMap("\"hello\"", "bean.get()"));

        // Wait until we get "uuid"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> DevModeTestUtils.getHttpResponse("/app/hello").contains("message"));

        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(source::isFile);

        filter(source, Collections.singletonMap("message", "foobarbaz"));

        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES).until(() -> DevModeTestUtils.getHttpResponse("/app/hello").contains("foobarbaz"));
    }

    @Test
    public void testNoErrorMessageWhenNoTarget() throws IOException, MavenInvocationException {
        testDir = initProject("projects/classic", "projects/project-no-target");
        FileUtils.deleteQuietly(new File(testDir, "target"));

        runAndCheck();
    }

    @Test
    public void testNoErrorMessageWhenNoTargetClasses() throws IOException, MavenInvocationException {
        testDir = initProject("projects/classic", "projects/project-no-classes");
        new File(testDir, "target").mkdirs();
        // Be sure we don't have classes.
        FileUtils.deleteQuietly(new File(testDir, "target/classes"));

        runAndCheck();
    }

    @Test
    public void testThatTheApplicationIsReloadedOnDotEnvConfigChange() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic", "projects/project-dotenv");
        assertThat(testDir).isDirectory();
        running = new RunningInvoker(testDir, false);
        final Properties mvnRunProps = new Properties();
        mvnRunProps.setProperty("debug", "false");
        running.execute(Arrays.asList("compile", "quarkus:dev"), Collections.emptyMap(), mvnRunProps);

        String resp = DevModeTestUtils.getHttpResponse();

        assertThat(resp).containsIgnoringCase("ready").containsIgnoringCase("application").containsIgnoringCase("org.acme")
                .containsIgnoringCase("1.0-SNAPSHOT");

        String greeting = DevModeTestUtils.getHttpResponse("/app/hello/otherGreeting");
        assertThat(greeting).containsIgnoringCase("Hola");

        File source = new File(testDir, ".env");
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(source::isFile);

        String uuid = UUID.randomUUID().toString();
        filter(source, Collections.singletonMap("Hola", uuid));

        // Wait until we get "uuid"
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .until(() -> DevModeTestUtils.getHttpResponse("/app/hello/otherGreeting").contains(uuid));
    }

    @Test
    public void testDevModeWithoutJavaSrc() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic-no-java-src");
        run(true);
        getHttpResponse();

        assertThat(running.log()).contains("The project's sources directory does not exist");
    }

    @Test
    public void testMultiModuleDevModeWithoutJavaSrc() throws MavenInvocationException, IOException {
        testDir = initProject("projects/multimodule", "projects/multimodule-no-java-src");
        runAndCheck();

        assertThat(running.log()).doesNotContain("The project's sources directory does not exist");
    }

    @Test
    public void testThatTheApplicationIsNotStartedWithoutBuildGoal() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic-no-build");
        run(true);

        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> running.log().contains("skipping quarkus:dev as this is assumed to be a support library"));
    }

    @Test
    public void testThatTheApplicationIsStartedWithoutBuildGoalWhenNotEnforced() throws MavenInvocationException, IOException {
        testDir = initProject("projects/classic-no-build", "projects/classic-no-build-not-enforced");
        runAndCheck("-Dquarkus.enforceBuildGoal=false");

        assertThat(running.log()).doesNotContain("skipping quarkus:dev as this is assumed to be a support library");
    }

    @Test
    public void testResourcesFromClasspath() throws MavenInvocationException, IOException, InterruptedException {
        testDir = initProject("projects/multimodule-classpath", "projects/multimodule-resources-classpath");
        RunningInvoker invoker = new RunningInvoker(testDir, false);

        // to properly surface the problem of multiple classpath entries, we need to install the project to the local m2
        MavenProcessInvocationResult installInvocation = invoker.execute(Arrays.asList("clean", "install", "-DskipTests"),
                Collections.emptyMap());
        assertThat(installInvocation.getProcess().waitFor(2, TimeUnit.MINUTES)).isTrue();
        assertThat(installInvocation.getExecutionException()).isNull();
        assertThat(installInvocation.getExitCode()).isEqualTo(0);

        // run dev mode from the runner module
        testDir = testDir.toPath().resolve("runner").toFile();
        run(true);

        // make sure the application starts
        await()
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(20, TimeUnit.SECONDS)
                .until(() -> DevModeTestUtils.getHttpResponse("/cp/hello").equals("hello"));

        // test that we don't get multiple instances of a resource when loading from the ClassLoader
        assertThat(DevModeTestUtils.getHttpResponse("/cp/resourcesCount")).isEqualTo("1");
    }

    @Test
    public void testThatDependencyInParentIsEvaluated() throws IOException, MavenInvocationException {
        testDir = initProject("projects/multimodule-parent-dep");
        runAndCheck();
    }
}
