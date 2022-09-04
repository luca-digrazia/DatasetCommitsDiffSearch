package io.quarkus.rest.test.simple;

import java.util.List;

import javax.ws.rs.BeanParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.Assertions;

import io.quarkus.rest.runtime.core.parameters.converters.ParameterConverter;
import io.quarkus.rest.runtime.injection.QuarkusRestInjectionContext;

public class BeanParamSuperClass {
    @QueryParam("query")
    String query;

    @QueryParam("query")
    private String privateQuery;

    @QueryParam("query")
    protected String protectedQuery;

    @QueryParam("query")
    public String publicQuery;

    @HeaderParam("header")
    String header;

    @Context
    UriInfo uriInfo;

    @BeanParam
    OtherBeanParam otherBeanParam;

    @QueryParam("queryList")
    List<String> queryList;

    @QueryParam("query")
    ParameterWithFromString parameterWithFromString;

    @QueryParam("missing")
    String missing;

    @DefaultValue("there")
    @QueryParam("missing")
    String missingWithDefaultValue;

    @QueryParam("missing")
    ParameterWithFromString missingParameterWithFromString;

    @DefaultValue("there")
    @QueryParam("missing")
    ParameterWithFromString missingParameterWithFromStringAndDefaultValue;

    @QueryParam("int")
    int primitiveParam;

    @QueryParam("missing")
    int missingPrimitiveParam;

    @DefaultValue("42")
    @QueryParam("missing")
    int missingPrimitiveParamWithDefaultValue;

    static class MyConverter implements ParameterConverter {

        @Override
        public Object convert(Object parameter) {
            return ParameterWithFromString.fromString((String) parameter);
        }

    }

    void inject(QuarkusRestInjectionContext ctx) {
        try {
            Object param = ctx.getQueryParameter("query", true, false);
            if (param == null)
                param = "default-value";
            if (param != null)
                parameterWithFromString = (ParameterWithFromString) new MyConverter().convert(param);
        } catch (WebApplicationException x) {
            throw x;
        } catch (Throwable x) {
            throw new NotFoundException(x);
        }
    }

    public void check(String path) {
        Assertions.assertEquals("one-query", query);
        Assertions.assertEquals("one-query", privateQuery);
        Assertions.assertEquals("one-query", protectedQuery);
        Assertions.assertEquals("one-query", publicQuery);
        Assertions.assertEquals("one-header", header);
        Assertions.assertNotNull(uriInfo);
        Assertions.assertEquals(path, uriInfo.getPath());
        Assertions.assertNotNull(otherBeanParam);
        otherBeanParam.check(path);
        Assertions.assertNotNull(queryList);
        Assertions.assertEquals("one", queryList.get(0));
        Assertions.assertEquals("two", queryList.get(1));
        Assertions.assertNotNull(parameterWithFromString);
        Assertions.assertEquals("ParameterWithFromString[val=one-query]", parameterWithFromString.toString());
        Assertions.assertNull(missing);
        Assertions.assertEquals("there", missingWithDefaultValue);
        Assertions.assertNull(missingParameterWithFromString);
        Assertions.assertEquals("ParameterWithFromString[val=there]", missingParameterWithFromStringAndDefaultValue.toString());
        Assertions.assertEquals(666, primitiveParam);
        Assertions.assertEquals(0, missingPrimitiveParam);
        Assertions.assertEquals(42, missingPrimitiveParamWithDefaultValue);
    }
}
