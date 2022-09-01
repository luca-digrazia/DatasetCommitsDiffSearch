package io.quarkus.devtools.codestarts.core.strategy;

import io.quarkus.devtools.codestarts.CodestartStructureException;
import io.quarkus.devtools.codestarts.core.reader.TargetFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface CodestartFileStrategyHandler {

    DefaultCodestartFileStrategyHandler DEFAULT_STRATEGY = new FailOnDuplicateCodestartFileStrategyHandler();

    Map<String, CodestartFileStrategyHandler> BY_NAME = Stream
            .of(DEFAULT_STRATEGY,
                    new AppendCodestartFileStrategyHandler(),
                    new ExecutableFileStrategyHandler(),
                    new ReplaceCodestartFileStrategyHandler(),
                    new ForbiddenCodestartFileStrategyHandler(),
                    new SmartConfigMergeCodestartFileStrategyHandler(),
                    new SmartPomMergeCodestartFileStrategyHandler(),
                    new NativeTestDirResolveCodestartFileStrategyHandler())
            .collect(Collectors.toMap(CodestartFileStrategyHandler::name, Function.identity()));

    String name();

    void process(Path targetDirectory, String relativePath, List<TargetFile> codestartFiles, Map<String, Object> data)
            throws IOException;

    default void checkNotEmptyCodestartFiles(List<TargetFile> codestartFiles) {
        if (codestartFiles == null || codestartFiles.isEmpty()) {
            throw new CodestartStructureException("codestartFiles must not be null or empty");
        }
    }

    default void checkTargetDoesNotExist(Path targetPath) {
        if (Files.exists(targetPath)) {
            throw new CodestartStructureException(
                    "Target file already exists: " + targetPath.toString());
        }
    }

    default void createDirectories(Path targetPath) throws IOException {
        Files.createDirectories(targetPath.getParent());
    }

}
