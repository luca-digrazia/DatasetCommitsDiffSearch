package com.codahale.dropwizard.views.mustache;

import com.codahale.dropwizard.views.View;
import com.codahale.dropwizard.views.ViewRenderer;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.MustacheFactory;
import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;

import javax.ws.rs.WebApplicationException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MustacheViewRenderer implements ViewRenderer {
    private final LoadingCache<Class<? extends View>, MustacheFactory> factories;

    public MustacheViewRenderer() {
        this.factories = CacheBuilder.newBuilder()
                                     .build(new CacheLoader<Class<? extends View>, MustacheFactory>() {
                                         @Override
                                         public MustacheFactory load(Class<? extends View> key) throws Exception {
                                             return new CachingMustacheFactory(key);
                                         }
                                     });
    }

    @Override
    public boolean isRenderable(View view) {
        return view.getTemplateName().endsWith(".mustache");
    }

    @Override
    public void render(View view, Locale locale, OutputStream output) throws IOException, WebApplicationException {
        try (OutputStreamWriter writer = new OutputStreamWriter(output, view.getCharset().or(Charsets.UTF_8))) {
            try {
                final Mustache template = factories.get(view.getClass()).compile(view.getTemplateName());
                template.execute(writer, view);
            } catch (ExecutionException | MustacheException | UncheckedExecutionException ignored) {
                // TODO: 4/26/13 <coda> -- fix exception handing
                throw new FileNotFoundException("Can't find " + view.getTemplateName());
            }
        }
    }
}
