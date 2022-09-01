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

package org.jboss.shamrock.deployment.steps;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.jboss.shamrock.deployment.annotations.BuildProducer;
import org.jboss.shamrock.deployment.annotations.BuildStep;
import org.jboss.shamrock.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import org.jboss.shamrock.deployment.builditem.SslNativeConfigBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.RuntimeInitializedClassBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.RuntimeReinitializedClassBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.SubstrateConfigBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.SubstrateProxyDefinitionBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.SubstrateResourceBundleBuildItem;
import org.jboss.shamrock.deployment.builditem.substrate.SubstrateSystemPropertyBuildItem;

//TODO: this should go away, once we decide on which one of the API's we want
class SubstrateConfigBuildStep {

    private static final Logger log = Logger.getLogger(SubstrateConfigBuildStep.class);

    @BuildStep
    void build(List<SubstrateConfigBuildItem> substrateConfigBuildItems,
               SslNativeConfigBuildItem sslNativeConfig,
               List<ExtensionSslNativeSupportBuildItem> extensionSslNativeSupport,
               BuildProducer<SubstrateProxyDefinitionBuildItem> proxy,
               BuildProducer<SubstrateResourceBundleBuildItem> resourceBundle,
               BuildProducer<RuntimeInitializedClassBuildItem> runtimeInit,
               BuildProducer<RuntimeReinitializedClassBuildItem> runtimeReinit,
               BuildProducer<SubstrateSystemPropertyBuildItem> nativeImage) {
        for (SubstrateConfigBuildItem substrateConfigBuildItem : substrateConfigBuildItems) {
            for (String i : substrateConfigBuildItem.getRuntimeInitializedClasses()) {
                runtimeInit.produce(new RuntimeInitializedClassBuildItem(i));
            }
            for (String i : substrateConfigBuildItem.getRuntimeReinitializedClasses()) {
                runtimeReinit.produce(new RuntimeReinitializedClassBuildItem(i));
            }
            for (Map.Entry<String, String> e : substrateConfigBuildItem.getNativeImageSystemProperties().entrySet()) {
                nativeImage.produce(new SubstrateSystemPropertyBuildItem(e.getKey(), e.getValue()));
            }
            for (String i : substrateConfigBuildItem.getResourceBundles()) {
                resourceBundle.produce(new SubstrateResourceBundleBuildItem(i));
            }
            for (List<String> i : substrateConfigBuildItem.getProxyDefinitions()) {
                proxy.produce(new SubstrateProxyDefinitionBuildItem(i));
            }
        }

        if (sslNativeConfig.isEnabled()) {
            nativeImage.produce(new SubstrateSystemPropertyBuildItem("shamrock.ssl.native", "true"));
        } else if (!sslNativeConfig.isExplicitlyDisabled() && !extensionSslNativeSupport.isEmpty()) {
            // we have extensions desiring the SSL support and it's not explicitly disabled
            nativeImage.produce(new SubstrateSystemPropertyBuildItem("shamrock.ssl.native", "true"));

            if (log.isDebugEnabled()) {
                log.debugf("Native SSL support enabled due to extensions [%s] requiring it",
                        extensionSslNativeSupport.stream().map(s -> s.getExtension()).collect(Collectors.joining(", ")));
            }
        } else {
            nativeImage.produce(new SubstrateSystemPropertyBuildItem("shamrock.ssl.native", "false"));
        }
    }
}
