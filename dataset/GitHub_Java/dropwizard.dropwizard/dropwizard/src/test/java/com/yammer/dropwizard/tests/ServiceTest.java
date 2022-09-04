package com.yammer.dropwizard.tests;

import com.yammer.dropwizard.Module;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Configuration;
import com.yammer.dropwizard.config.Environment;
import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class ServiceTest {
    @SuppressWarnings({"PackageVisibleInnerClass", "EmptyClass"})
    static class FakeConfiguration extends Configuration {

    }

    private class FakeService extends Service<FakeConfiguration> {
        FakeService() {
            super("test");
            addModule(module);
            setBanner("woo");
        }

        @Override
        protected void initialize(FakeConfiguration configuration,
                                  Environment environment) {
        }
    }

    private final Module module = mock(Module.class);
    private final FakeService service = new FakeService();

    @Test
    public void hasAReferenceToItsTypeParameter() throws Exception {
        assertThat(service.getConfigurationClass(),
                   is(sameInstance(FakeConfiguration.class)));
    }

    @Test
    public void mightHaveABanner() throws Exception {
        assertThat(service.hasBanner(),
                   is(true));
        
        assertThat(service.getBanner(),
                   is("woo"));
    }
}
