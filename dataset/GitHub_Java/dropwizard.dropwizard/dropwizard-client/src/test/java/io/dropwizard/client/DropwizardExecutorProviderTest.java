package io.dropwizard.client;

import org.glassfish.jersey.spi.ExecutorServiceProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class DropwizardExecutorProviderTest {
    @Test
    public void doesntShutDownNonDisposableExecutorService() {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final ExecutorServiceProvider provider = new DropwizardExecutorProvider(executor);

        assertThat(executor.isShutdown()).isFalse();
        provider.dispose(executor);
        assertThat(executor.isShutdown()).isFalse();
        executor.shutdown();
    }

    @Test
    public void shutsDownDisposableExecutorService() {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final ExecutorService disposableExecutor =
            new DropwizardExecutorProvider.DisposableExecutorService(executor);

        final ExecutorServiceProvider provider =
            new DropwizardExecutorProvider(disposableExecutor);

        assertThat(executor.isShutdown()).isFalse();
        provider.dispose(disposableExecutor);
        assertThat(executor.isShutdown()).isTrue();
    }
}
