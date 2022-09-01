package io.quarkus.deployment.steps;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.jboss.logging.Logger;

import io.quarkus.banner.BannerConfig;
import io.quarkus.builder.Version;
import io.quarkus.deployment.IsTest;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ConsoleFormatterBannerBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.util.FileUtil;
import io.quarkus.runtime.BannerRecorder;
import io.quarkus.runtime.BannerRuntimeConfig;

public class BannerProcessor {

    private static final Logger logger = Logger.getLogger(BannerProcessor.class);

    @BuildStep(loadsApplicationClasses = true, onlyIfNot = { IsTest.class })
    @Record(ExecutionTime.RUNTIME_INIT)
    public ConsoleFormatterBannerBuildItem recordBanner(BannerRecorder recorder, BannerConfig config,
            BannerRuntimeConfig bannerRuntimeConfig) {
        String bannerText = readBannerFile(config);
        return new ConsoleFormatterBannerBuildItem(recorder.provideBannerSupplier(bannerText, bannerRuntimeConfig));
    }

    @BuildStep
    HotDeploymentWatchedFileBuildItem watchBannerChanges(BannerConfig config) {
        return new HotDeploymentWatchedFileBuildItem(config.path);
    }

    private String readBannerFile(BannerConfig config) {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(config.path);
        if (resource != null) {
            try (InputStream is = resource.openStream()) {
                byte[] content = FileUtil.readFileContents(is);
                String bannerTitle = new String(content, StandardCharsets.UTF_8);

                int width = 0;
                Scanner scanner = new Scanner(bannerTitle);
                while (scanner.hasNextLine()) {
                    width = Math.max(width, scanner.nextLine().length());
                }

                String tagline = "\n";
                if (!config.isDefaultPath()) {
                    tagline = String.format("\n%" + width + "s\n", "Powered by Quarkus v" + Version.getVersion());
                }

                return bannerTitle + tagline;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            logger.warn("Could not read banner file");
            return "";
        }
    }
}
