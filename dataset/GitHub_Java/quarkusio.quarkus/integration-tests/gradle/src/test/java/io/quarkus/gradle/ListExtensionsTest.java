package io.quarkus.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
// TODO: CLI work. We know the gradle plugin works (cli tests),
// this isn't seeing the output in the same way
public class ListExtensionsTest extends QuarkusGradleDevToolsTestBase {

    @Test
    public void testListExtensionsWork() throws IOException, URISyntaxException, InterruptedException {

        final File projectDir = getProjectDir("list-extension-single-module");
        runGradleWrapper(projectDir, ":listExtensions");

        List<String> outputLogLines = listExtensions(projectDir, ":listExtension");

        assertThat(outputLogLines).anyMatch(line -> line.contains("quarkus-resteasy"));
        assertThat(outputLogLines).anyMatch(line -> line.contains("quarkus-vertx"));
    }

    private List<String> listExtensions(File projectDir, String... args) throws IOException, InterruptedException {

        File outputLog = new File(projectDir, "command-output.log");

        return Files.readAllLines(outputLog.toPath());
    }

}