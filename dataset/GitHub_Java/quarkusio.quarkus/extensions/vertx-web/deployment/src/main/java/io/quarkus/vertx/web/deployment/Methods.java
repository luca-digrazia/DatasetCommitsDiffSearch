package io.quarkus.vertx.web.deployment;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;

import org.jboss.jandex.DotName;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InjectableContext;
import io.quarkus.arc.InjectableReferenceProvider;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.vertx.web.runtime.MultiJsonArraySupport;
import io.quarkus.vertx.web.runtime.MultiSseSupport;
import io.quarkus.vertx.web.runtime.MultiSupport;
import io.quarkus.vertx.web.runtime.RouteHandlers;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.groups.UniSubscribe;
import io.smallrye.mutiny.subscription.Cancellable;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

class Methods {

    static final MethodDescriptor GET_HEADERS = MethodDescriptor
            .ofMethod(HttpServerResponse.class, "headers", MultiMap.class);
    static final MethodDescriptor MULTIMAP_GET = MethodDescriptor
            .ofMethod(MultiMap.class, "get", String.class, String.class);
    static final MethodDescriptor MULTIMAP_SET = MethodDescriptor
            .ofMethod(MultiMap.class, "set", MultiMap.class, String.class, String.class);
    static final MethodDescriptor MULTIMAP_GET_ALL = MethodDescriptor
            .ofMethod(MultiMap.class, "getAll", List.class, String.class);

    static final MethodDescriptor REQUEST = MethodDescriptor
            .ofMethod(RoutingContext.class, "request", HttpServerRequest.class);
    static final MethodDescriptor REQUEST_GET_PARAM = MethodDescriptor
            .ofMethod(HttpServerRequest.class, "getParam", String.class, String.class);
    static final MethodDescriptor REQUEST_GET_HEADER = MethodDescriptor
            .ofMethod(HttpServerRequest.class, "getHeader", String.class, String.class);
    static final MethodDescriptor GET_BODY = MethodDescriptor
            .ofMethod(RoutingContext.class, "getBody", Buffer.class);
    static final MethodDescriptor GET_BODY_AS_STRING = MethodDescriptor
            .ofMethod(RoutingContext.class, "getBodyAsString", String.class);
    static final MethodDescriptor GET_BODY_AS_JSON = MethodDescriptor
            .ofMethod(RoutingContext.class, "getBodyAsJson", JsonObject.class);
    static final MethodDescriptor GET_BODY_AS_JSON_ARRAY = MethodDescriptor
            .ofMethod(RoutingContext.class, "getBodyAsJsonArray", JsonArray.class);
    static final MethodDescriptor JSON_OBJECT_MAP_TO = MethodDescriptor
            .ofMethod(JsonObject.class, "mapTo", Object.class, Class.class);
    static final MethodDescriptor REQUEST_PARAMS = MethodDescriptor
            .ofMethod(HttpServerRequest.class, "params", MultiMap.class);
    static final MethodDescriptor REQUEST_HEADERS = MethodDescriptor
            .ofMethod(HttpServerRequest.class, "headers", MultiMap.class);

    static final MethodDescriptor RESPONSE = MethodDescriptor
            .ofMethod(RoutingContext.class, "response", HttpServerResponse.class);

    static final MethodDescriptor FAIL = MethodDescriptor
            .ofMethod(RoutingContext.class, "fail", Void.TYPE, Throwable.class);

    static final MethodDescriptor UNI_SUBSCRIBE = MethodDescriptor.ofMethod(Uni.class, "subscribe", UniSubscribe.class);
    static final MethodDescriptor UNI_SUBSCRIBE_WITH = MethodDescriptor
            .ofMethod(UniSubscribe.class, "with", Cancellable.class, Consumer.class, Consumer.class);

    static final MethodDescriptor MULTI_SUBSCRIBE_VOID = MethodDescriptor.ofMethod(MultiSupport.class, "subscribeVoid",
            Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_SUBSCRIBE_STRING = MethodDescriptor.ofMethod(MultiSupport.class, "subscribeString",
            Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_SUBSCRIBE_BUFFER = MethodDescriptor.ofMethod(MultiSupport.class, "subscribeBuffer",
            Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_SUBSCRIBE_RX_BUFFER = MethodDescriptor.ofMethod(MultiSupport.class, "subscribeRxBuffer",
            Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_SUBSCRIBE_MUTINY_BUFFER = MethodDescriptor.ofMethod(MultiSupport.class,
            "subscribeMutinyBuffer", Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_SUBSCRIBE_OBJECT = MethodDescriptor.ofMethod(MultiSupport.class, "subscribeObject",
            Void.TYPE, Multi.class, RoutingContext.class);

    static final MethodDescriptor IS_SSE = MethodDescriptor.ofMethod(MultiSseSupport.class, "isSSE", Boolean.TYPE, Multi.class);
    static final MethodDescriptor MULTI_SSE_SUBSCRIBE_STRING = MethodDescriptor.ofMethod(MultiSseSupport.class,
            "subscribeString",
            Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_SSE_SUBSCRIBE_BUFFER = MethodDescriptor.ofMethod(MultiSseSupport.class,
            "subscribeBuffer",
            Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_SSE_SUBSCRIBE_RX_BUFFER = MethodDescriptor.ofMethod(MultiSseSupport.class,
            "subscribeRxBuffer",
            Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_SSE_SUBSCRIBE_MUTINY_BUFFER = MethodDescriptor.ofMethod(MultiSseSupport.class,
            "subscribeMutinyBuffer", Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_SSE_SUBSCRIBE_OBJECT = MethodDescriptor.ofMethod(MultiSseSupport.class,
            "subscribeObject",
            Void.TYPE, Multi.class, RoutingContext.class);

    static final MethodDescriptor IS_JSON_ARRAY = MethodDescriptor.ofMethod(MultiJsonArraySupport.class, "isJsonArray",
            Boolean.TYPE, Multi.class);
    static final MethodDescriptor MULTI_JSON_SUBSCRIBE_VOID = MethodDescriptor.ofMethod(MultiJsonArraySupport.class,
            "subscribeVoid", Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_JSON_SUBSCRIBE_STRING = MethodDescriptor.ofMethod(MultiJsonArraySupport.class,
            "subscribeString", Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_JSON_SUBSCRIBE_BUFFER = MethodDescriptor.ofMethod(MultiJsonArraySupport.class,
            "subscribeBuffer", Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_JSON_SUBSCRIBE_RX_BUFFER = MethodDescriptor.ofMethod(MultiJsonArraySupport.class,
            "subscribeRxBuffer", Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_JSON_SUBSCRIBE_MUTINY_BUFFER = MethodDescriptor.ofMethod(MultiJsonArraySupport.class,
            "subscribeMutinyBuffer", Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_JSON_SUBSCRIBE_OBJECT = MethodDescriptor.ofMethod(MultiJsonArraySupport.class,
            "subscribeObject", Void.TYPE, Multi.class, RoutingContext.class);
    static final MethodDescriptor MULTI_JSON_FAIL = MethodDescriptor.ofMethod(MultiJsonArraySupport.class,
            "fail", Void.TYPE, RoutingContext.class);

    static final MethodDescriptor END = MethodDescriptor.ofMethod(HttpServerResponse.class, "end", Void.TYPE);
    static final MethodDescriptor END_WITH_STRING = MethodDescriptor
            .ofMethod(HttpServerResponse.class, "end", Void.TYPE, String.class);
    static final MethodDescriptor END_WITH_BUFFER = MethodDescriptor
            .ofMethod(HttpServerResponse.class, "end", Void.TYPE, Buffer.class);
    static final MethodDescriptor SET_STATUS = MethodDescriptor
            .ofMethod(HttpServerResponse.class, "setStatusCode", HttpServerResponse.class, Integer.TYPE);
    static final MethodDescriptor RX_GET_DELEGATE = MethodDescriptor
            .ofMethod(io.vertx.reactivex.core.buffer.Buffer.class, "getDelegate", Buffer.class);

    static final MethodDescriptor MUTINY_GET_DELEGATE = MethodDescriptor
            .ofMethod(io.vertx.mutiny.core.buffer.Buffer.class, "getDelegate", Buffer.class);
    static final MethodDescriptor JSON_ENCODE = MethodDescriptor
            .ofMethod(Json.class, "encode", String.class, Object.class);
    static final MethodDescriptor ARC_CONTAINER = MethodDescriptor
            .ofMethod(Arc.class, "container", ArcContainer.class);
    static final MethodDescriptor ARC_CONTAINER_GET_ACTIVE_CONTEXT = MethodDescriptor
            .ofMethod(ArcContainer.class,
                    "getActiveContext", InjectableContext.class, Class.class);
    static final MethodDescriptor ARC_CONTAINER_BEAN = MethodDescriptor.ofMethod(ArcContainer.class, "bean",
            InjectableBean.class, String.class);
    static final MethodDescriptor BEAN_GET_SCOPE = MethodDescriptor.ofMethod(InjectableBean.class, "getScope",
            Class.class);
    static final MethodDescriptor CONTEXT_GET = MethodDescriptor.ofMethod(Context.class, "get", Object.class,
            Contextual.class,
            CreationalContext.class);
    static final MethodDescriptor CONTEXT_GET_IF_PRESENT = MethodDescriptor
            .ofMethod(Context.class, "get", Object.class,
                    Contextual.class);
    static final MethodDescriptor INJECTABLE_REF_PROVIDER_GET = MethodDescriptor.ofMethod(
            InjectableReferenceProvider.class,
            "get", Object.class,
            CreationalContext.class);
    static final MethodDescriptor INJECTABLE_BEAN_DESTROY = MethodDescriptor
            .ofMethod(InjectableBean.class, "destroy",
                    void.class, Object.class,
                    CreationalContext.class);
    static final MethodDescriptor OBJECT_CONSTRUCTOR = MethodDescriptor.ofConstructor(Object.class);

    static final MethodDescriptor ROUTE_HANDLERS_SET_CONTENT_TYPE = MethodDescriptor
            .ofMethod(RouteHandlers.class, "setContentType", void.class, RoutingContext.class);

    static final MethodDescriptor OPTIONAL_OF_NULLABLE = MethodDescriptor
            .ofMethod(Optional.class, "ofNullable", Optional.class, Object.class);

    private Methods() {
        // Avoid direct instantiation
    }

    static void returnAndClose(BytecodeCreator creator) {
        creator.returnValue(null);
        creator.close();
    }

    static boolean isNoContent(HandlerDescriptor descriptor) {
        return descriptor.getContentType().name()
                .equals(DotName.createSimple(Void.class.getName()));
    }

    static ResultHandle createNpeBecauseItemIfNull(BytecodeCreator writer) {
        return writer.newInstance(
                MethodDescriptor.ofConstructor(NullPointerException.class, String.class),
                writer.load("Invalid value returned by Uni: `null`"));
    }

    static MethodDescriptor getEndMethodForContentType(HandlerDescriptor descriptor) {
        if (descriptor.isContentTypeBuffer() || descriptor.isContentTypeRxBuffer() || descriptor
                .isContentTypeMutinyBuffer()) {
            return END_WITH_BUFFER;
        }
        return END_WITH_STRING;
    }

    static void setContentTypeToJson(ResultHandle response, BytecodeCreator invoke) {
        ResultHandle ct = invoke.load("Content-Type");
        ResultHandle headers = invoke.invokeInterfaceMethod(GET_HEADERS, response);
        ResultHandle current = invoke.invokeInterfaceMethod(MULTIMAP_GET, headers, ct);
        BytecodeCreator branch = invoke.ifNull(current).trueBranch();
        branch.invokeInterfaceMethod(MULTIMAP_SET, headers, ct, branch.load("application/json"));
        branch.close();
    }
}
