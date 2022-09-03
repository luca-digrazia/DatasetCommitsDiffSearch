package com.oracle.graal.microbenchmarks.lir;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.api.test.Graal;
import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.GraalCompiler;
import com.oracle.graal.compiler.GraalCompiler.Request;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.microbenchmarks.graal.util.GraphState;
import com.oracle.graal.nodes.spi.LoweringProvider;
import com.oracle.graal.options.DerivedOptionValue;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;
import com.oracle.graal.phases.tiers.TargetProvider;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.runtime.RuntimeProvider;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class GraalCompilerState extends GraphState {

    private final Backend backend;
    private final Providers providers;
    private final DerivedOptionValue<Suites> suites;
    private final DerivedOptionValue<LIRSuites> lirSuites;

    public GraalCompilerState() {
        this.backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        this.providers = backend.getProviders();
        this.suites = new DerivedOptionValue<>(this::createSuites);
        this.lirSuites = new DerivedOptionValue<>(this::createLIRSuites);

    }

    protected Suites createSuites() {
        Suites ret = backend.getSuites().getDefaultSuites().copy();
        return ret;
    }

    protected LIRSuites createLIRSuites() {
        LIRSuites ret = backend.getSuites().getDefaultLIRSuites().copy();
        return ret;
    }

    protected Backend getBackend() {
        return backend;
    }

    protected Suites getSuites() {
        return suites.getValue();
    }

    protected LIRSuites getLIRSuites() {
        return lirSuites.getValue();
    }

    protected Providers getProviders() {
        return providers;
    }

    protected SnippetReflectionProvider getSnippetReflection() {
        return Graal.getRequiredCapability(SnippetReflectionProvider.class);
    }

    protected TargetDescription getTarget() {
        return getTargetProvider().getTarget();
    }

    protected TargetProvider getTargetProvider() {
        return getBackend();
    }

    protected CodeCacheProvider getCodeCache() {
        return getProviders().getCodeCache();
    }

    protected ConstantReflectionProvider getConstantReflection() {
        return getProviders().getConstantReflection();
    }

    protected MetaAccessProvider getMetaAccess() {
        return getProviders().getMetaAccess();
    }

    protected LoweringProvider getLowerer() {
        return getProviders().getLowerer();
    }

    protected PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        // defensive copying
        return backend.getSuites().getDefaultGraphBuilderSuite().copy();
    }

    private Request<CompilationResult> request;

    @Setup(Level.Invocation)
    public void prepareRequest() {
        ResolvedJavaMethod installedCodeOwner = graph.method();
        request = new Request<>(graph, installedCodeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL,
                        graph.getProfilingInfo(), getSuites(), getLIRSuites(), new CompilationResult(), CompilationResultBuilderFactory.Default);
    }

    @SuppressWarnings("try")
    protected CompilationResult compile() {
        try (Scope s = Debug.scope("Compile", graph)) {
            assert !request.graph.isFrozen();
            try (Scope s0 = Debug.scope("GraalCompiler", request.graph, request.providers.getCodeCache())) {
                emitFrontEnd();
                emitBackEnd();
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            return request.compilationResult;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    protected void emitFrontEnd() {
        GraalCompiler.emitFrontEnd(request.providers, request.backend, request.graph, request.graphBuilderSuite, request.optimisticOpts, request.profilingInfo, request.suites);
    }

    protected void emitBackEnd() {
        GraalCompiler.emitBackEnd(request.graph, null, request.installedCodeOwner, request.backend, request.compilationResult, request.factory, null, request.lirSuites);
    }
}
