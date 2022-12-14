package io.quarkus.rest.runtime.core;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.rest.runtime.model.ResourceParamConverterProvider;

public class ParamConverterProviders {

    private final List<ResourceParamConverterProvider> paramConverterProviders = new ArrayList<>();

    public void addParamConverterProviders(ResourceParamConverterProvider resourceFeature) {
        paramConverterProviders.add(resourceFeature);
    }

    public List<ResourceParamConverterProvider> getParamConverterProviders() {
        return paramConverterProviders;
    }
}
