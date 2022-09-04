package com.yammer.dropwizard.views;

import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.sun.jersey.api.container.ContainerException;
import com.yammer.metrics.core.TimerContext;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;

@Provider
@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML})
public class ViewMessageBodyWriter implements MessageBodyWriter<View> {
    private static class TemplateLoader extends CacheLoader<Class<?>, Configuration> {
        @Override
        public Configuration load(Class<?> key) throws Exception {
            final Configuration configuration = new Configuration();
            configuration.setObjectWrapper(new DefaultObjectWrapper());
            configuration.setDefaultEncoding(Charsets.UTF_8.name());
            configuration.setClassForTemplateLoading(key, "/");
            return configuration;
        }
    }

    private static final String MISSING_TEMPLATE_MSG =
            "<html>" +
                "<head><title>Missing Template</title></head>" +
                "<body><h1>Missing Template</h1><p>{0}</p></body>" +
            "</html>";

    private final LoadingCache<Class<?>, Configuration> configurationCache;

    @Context
    @SuppressWarnings("FieldMayBeFinal")
    private HttpHeaders headers;

    @SuppressWarnings("UnusedDeclaration")
    public ViewMessageBodyWriter() {
        this(null);
    }

    public ViewMessageBodyWriter(HttpHeaders headers) {
        this.headers = headers;
        this.configurationCache = CacheBuilder.newBuilder()
                                              .concurrencyLevel(128)
                                              .build(new TemplateLoader());
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return View.class.isAssignableFrom(type);
    }

    @Override
    public long getSize(View t,
                        Class<?> type,
                        Type genericType,
                        Annotation[] annotations,
                        MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(View t,
                        Class<?> type,
                        Type genericType,
                        Annotation[] annotations,
                        MediaType mediaType,
                        MultivaluedMap<String, Object> httpHeaders,
                        OutputStream entityStream) throws IOException, WebApplicationException {
        final TimerContext context = t.getRenderingTimer().time();
        try {
            final Configuration configuration = configurationCache.getUnchecked(type);
            final Template template = configuration.getTemplate(t.getTemplateName(),
                                                                detectLocale(headers));
            template.process(t, new OutputStreamWriter(entityStream, template.getEncoding()));
        } catch (TemplateException e) {
            throw new ContainerException(e);
        } catch (FileNotFoundException e) {
            final String msg = MessageFormat.format(MISSING_TEMPLATE_MSG, e.getMessage());
            throw new WebApplicationException(Response.serverError()
                                                      .type(MediaType.TEXT_HTML_TYPE)
                                                      .entity(msg)
                                                      .build());
        } finally {
            context.stop();
        }
    }

    private Locale detectLocale(HttpHeaders headers) {
        final List<Locale> languages = headers.getAcceptableLanguages();
        for (Locale locale : languages) {
            if (!locale.toString().contains("*")) { // Freemarker doesn't do wildcards well
                return locale;
            }
        }
        return Locale.getDefault();
    }
}
