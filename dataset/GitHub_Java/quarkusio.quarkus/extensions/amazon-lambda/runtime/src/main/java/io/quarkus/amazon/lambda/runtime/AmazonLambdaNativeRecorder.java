package io.quarkus.amazon.lambda.runtime;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.Application;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Used with Amazon Lambda custom runtime.
 *
 */
@Recorder
public class AmazonLambdaNativeRecorder {

    private static final Logger log = Logger.getLogger(AmazonLambdaNativeRecorder.class);

    @SuppressWarnings("rawtypes")
    public void startNativePollLoop(Class<? extends RequestHandler<?, ?>> handlerClass, BeanContainer beanContainer,
            ShutdownContext context) {
        RequestHandler handler = beanContainer.instance(handlerClass);

        final ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        AtomicBoolean running = new AtomicBoolean(true);

        Class<?> handlerType = discoverParameterTypes(handlerClass);

        ObjectReader objectReader = mapper.readerFor(handlerType);
        ObjectReader cognitoIdReader = mapper.readerFor(CognitoIdentity.class);
        ObjectReader clientCtxReader = mapper.readerFor(ClientContext.class);

        context.addShutdownTask(new Runnable() {
            @Override
            public void run() {
                running.set(false);
            }
        });
        Thread t = new Thread(new Runnable() {
            @SuppressWarnings("unchecked")
            @Override
            public void run() {

                try {
                    checkQuarkusBootstrapped();
                    URL requestUrl = AmazonLambdaApi.invocationNext();
                    while (running.get()) {

                        HttpURLConnection requestConnection = (HttpURLConnection) requestUrl.openConnection();
                        try {
                            String requestId = requestConnection.getHeaderField(AmazonLambdaApi.LAMBDA_RUNTIME_AWS_REQUEST_ID);
                            Object response;
                            try {
                                Object val = objectReader.readValue(requestConnection.getInputStream());
                                response = handler.handleRequest(val,
                                        new AmazonLambdaContext(requestConnection, cognitoIdReader, clientCtxReader));
                            } catch (Exception e) {
                                log.error("Failed to run lambda", e);

                                postResponse(AmazonLambdaApi.invocationError(requestId),
                                        new FunctionError(e.getClass().getName(), e.getMessage()), mapper);
                                continue;
                            }

                            postResponse(AmazonLambdaApi.invocationResponse(requestId), response, mapper);
                        } catch (Exception e) {
                            log.error("Error running lambda", e);
                            Application app = Application.currentApplication();
                            if (app != null) {
                                app.stop();
                            }
                            return;
                        } finally {
                            requestConnection.getInputStream().close();
                        }

                    }

                } catch (Exception e) {
                    try {
                        log.error("Lambda init error", e);
                        postResponse(AmazonLambdaApi.initError(), new FunctionError(e.getClass().getName(), e.getMessage()),
                                mapper);
                    } catch (Exception ex) {
                        log.error("Failed to report init error", ex);
                    } finally {
                        //our main loop is done, time to shutdown
                        Application app = Application.currentApplication();
                        if (app != null) {
                            app.stop();
                        }
                    }
                }
            }
        }, "Lambda Thread");
        t.start();

    }

    private void checkQuarkusBootstrapped() {
        // todo we need a better way to do this.
        if (Application.currentApplication() == null) {
            throw new RuntimeException("Quarkus initialization error");
        }
        String[] args = {};
        Application.currentApplication().start(args);
    }

    private void postResponse(URL url, Object response, ObjectMapper mapper) throws IOException {
        HttpURLConnection responseConnection = (HttpURLConnection) url.openConnection();
        responseConnection.setDoOutput(true);
        responseConnection.setRequestMethod("POST");
        mapper.writeValue(responseConnection.getOutputStream(), response);
        while (responseConnection.getInputStream().read() != -1) {
            // Read data
        }
    }

    private Class<?> discoverParameterTypes(Class<? extends RequestHandler<?, ?>> handlerClass) {
        final Method[] methods = handlerClass.getMethods();
        Method method = null;
        for (int i = 0; i < methods.length && method == null; i++) {
            if (methods[i].getName().equals("handleRequest")) {
                final Class<?>[] types = methods[i].getParameterTypes();
                if (types.length == 2 && !types[0].equals(Object.class)) {
                    method = methods[i];
                }
            }
        }
        if (method == null) {
            method = methods[0];
        }
        return method.getParameterTypes()[0];
    }

}
