package org.jboss.shamrock.metrics;

import java.util.List;

import javax.inject.Inject;

import org.eclipse.microprofile.metrics.annotation.Counted;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.MethodInfo;
import org.jboss.shamrock.deployment.ArchiveContext;
import org.jboss.shamrock.deployment.ProcessorContext;
import org.jboss.shamrock.deployment.ResourceProcessor;
import org.jboss.shamrock.deployment.RuntimePriority;
import org.jboss.shamrock.deployment.ShamrockConfig;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.shamrock.metrics.runtime.MetricsDeploymentTemplate;
import org.jboss.shamrock.metrics.runtime.MetricsServlet;
import org.jboss.shamrock.undertow.ServletData;
import org.jboss.shamrock.undertow.ServletDeployment;
import org.jboss.shamrock.weld.deployment.WeldDeployment;

import io.smallrye.metrics.MetricProducer;
import io.smallrye.metrics.MetricRegistries;
import io.smallrye.metrics.MetricsRequestHandler;
import io.smallrye.metrics.interceptors.CountedInterceptor;
import io.smallrye.metrics.interceptors.MeteredInterceptor;
import io.smallrye.metrics.interceptors.MetricNameFactory;
import io.smallrye.metrics.interceptors.MetricsBinding;
import io.smallrye.metrics.interceptors.MetricsInterceptor;
import io.smallrye.metrics.interceptors.TimedInterceptor;

//import io.smallrye.health.SmallRyeHealthReporter;

public class MetricsProcessor implements ResourceProcessor {


    @Inject
    private WeldDeployment weldDeployment;

    @Inject
    private ShamrockConfig config;

    @Inject
    private ServletDeployment servletDeployment;

    @Override
    public void process(ArchiveContext archiveContext, ProcessorContext processorContext) throws Exception {
        ServletData servletData = new ServletData("metrics", MetricsServlet.class.getName());
        servletData.getMapings().add(config.getConfig("metrics.path", "/metrics"));
        servletDeployment.addServlet(servletData);

        weldDeployment.addAdditionalBean(MetricProducer.class,
                                            MetricNameFactory.class,
                                            MetricRegistries.class);

        weldDeployment.addAdditionalBean(MetricsInterceptor.class,
                                            MeteredInterceptor.class,
                                            CountedInterceptor.class,
                                            TimedInterceptor.class);

        weldDeployment.addAdditionalBean(MetricsRequestHandler.class, MetricsServlet.class);
        //weldDeployment.addInterceptor(MetricsInterceptor.class);
        //weldDeployment.addInterceptor(MeteredInterceptor.class);
        //weldDeployment.addInterceptor(CountedInterceptor.class);
        //weldDeployment.addInterceptor(TimedInterceptor.class);

        processorContext.addReflectiveClass(Counted.class.getName(), MetricsBinding.class.getName());


        try (BytecodeRecorder recorder = processorContext.addStaticInitTask(RuntimePriority.WELD_DEPLOYMENT + 30)) {
            MetricsDeploymentTemplate metrics = recorder.getRecordingProxy(MetricsDeploymentTemplate.class);

            metrics.createRegistries();

            Index index = archiveContext.getIndex();
            List<AnnotationInstance> annos = index.getAnnotations(DotName.createSimple(Counted.class.getName()));

            for (AnnotationInstance anno : annos) {
                AnnotationTarget target = anno.target();

                MethodInfo methodInfo = target.asMethod();
                ClassInfo classInfo = methodInfo.declaringClass();

                metrics.registerCounted(classInfo.name().toString(),
                        methodInfo.name().toString());

                processorContext.addReflectiveClass(classInfo.name().toString());
            }

        }
        try (BytecodeRecorder recorder = processorContext.addDeploymentTask(RuntimePriority.WELD_DEPLOYMENT + 30)) {
            MetricsDeploymentTemplate metrics = recorder.getRecordingProxy(MetricsDeploymentTemplate.class);
            metrics.registerBaseMetrics();
            metrics.registerVendorMetrics();
        }
    }

    @Override
    public int getPriority() {
        return 1;
    }
}
