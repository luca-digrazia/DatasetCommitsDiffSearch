package io.quarkus.cli.build;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.quarkus.cli.common.HelpOption;
import io.quarkus.cli.common.OutputOptionMixin;
import io.quarkus.cli.common.PropertiesOptions;
import io.quarkus.cli.common.ToggleRegistryClientMixin;
import io.quarkus.devtools.project.BuildTool;
import io.quarkus.devtools.project.QuarkusProjectHelper;
import picocli.CommandLine;

public class BaseBuildCommand {
    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    @CommandLine.Mixin
    protected OutputOptionMixin output;

    @CommandLine.Mixin
    protected ToggleRegistryClientMixin registryClient;

    @CommandLine.Mixin
    protected HelpOption helpOption;

    @CommandLine.ArgGroup(exclusive = false, validate = false)
    protected PropertiesOptions propertiesOptions = new PropertiesOptions();

    Path projectRoot;

    public Path projectRoot() {
        if (projectRoot == null) {
            projectRoot = output.getTestDirectory();
            if (projectRoot == null) {
                projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            }
        }
        return projectRoot;
    }

    public BuildSystemRunner getRunner() {
        BuildTool buildTool = QuarkusProjectHelper.detectExistingBuildTool(projectRoot()); // nullable
        return BuildSystemRunner.getRunner(output, propertiesOptions, registryClient, projectRoot(), buildTool);
    }
}
