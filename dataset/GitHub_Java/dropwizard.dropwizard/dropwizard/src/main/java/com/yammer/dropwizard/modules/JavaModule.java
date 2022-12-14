package com.yammer.dropwizard.modules;

import com.yammer.dropwizard.Module;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.jersey.JacksonMessageBodyProvider;
import com.yammer.dropwizard.jersey.OauthTokenProvider;

public class JavaModule implements Module {
    @Override
    public void initialize(Environment environment) {
        environment.addProvider(new JacksonMessageBodyProvider());
        environment.addProvider(new OauthTokenProvider());
    }
}
