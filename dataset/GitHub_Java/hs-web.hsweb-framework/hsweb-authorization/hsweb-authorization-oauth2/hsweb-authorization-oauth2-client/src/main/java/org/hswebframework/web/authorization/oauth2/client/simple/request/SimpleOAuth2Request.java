/*
 *  Copyright 2016 http://www.hswebframework.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package org.hswebframework.web.authorization.oauth2.client.simple.request;

import org.hswebframework.expands.request.http.HttpRequest;
import org.hswebframework.expands.request.http.Response;
import org.hswebframework.web.authorization.oauth2.client.request.OAuth2Request;
import org.hswebframework.web.authorization.oauth2.client.request.ResponseConvertHandler;
import org.hswebframework.web.authorization.oauth2.client.request.ResponseJudge;
import org.hswebframework.web.authorization.oauth2.client.request.TokenExpiredCallBack;
import org.hswebframework.web.authorization.oauth2.client.response.OAuth2Response;
import org.hswebframework.web.oauth2.core.ErrorType;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author zhouhao
 */
public class SimpleOAuth2Request implements OAuth2Request {

    private HttpRequest request;

    private ResponseConvertHandler convertHandler;

    private ResponseJudge responseJudge;

    private TokenExpiredCallBack expiredCallBack;

    private TokenExpiredCallBack refreshTokenExpiredCallBack;

    public SimpleOAuth2Request(HttpRequest request) {
        this.request = request;
    }

    public void setConvertHandler(ResponseConvertHandler convertHandler) {
        this.convertHandler = convertHandler;
    }

    public void setResponseJudge(ResponseJudge responseJudge) {
        this.responseJudge = responseJudge;
    }

    @Override
    public OAuth2Request onRefreshTokenExpired(TokenExpiredCallBack refreshTokenExpiredCallBack){
        this.refreshTokenExpiredCallBack=refreshTokenExpiredCallBack;
        return this;
    }

    @Override
    public OAuth2Request onTokenExpired(TokenExpiredCallBack callback) {
        this.expiredCallBack = callback;
        return this;
    }

    @Override
    public OAuth2Request params(Map<String, String> params) {
        request.params(params);
        return this;
    }

    @Override
    public OAuth2Request param(String name, Object value) {
        request.param(name, String.valueOf(value));
        return this;
    }

    @Override
    public OAuth2Request requestBody(String value) {
        request.requestBody(value);
        return this;
    }

    @Override
    public OAuth2Request header(String name, String value) {
        request.header(name, value);
        return this;
    }

    @Override
    public OAuth2Request cookie(String cookie) {
        request.cookie(cookie);
        return this;
    }

    @Override
    public OAuth2Request contentType(String contentType) {
        request.contentType(contentType);
        return this;
    }

    @Override
    public OAuth2Request accept(String accept) {
        header("Accept", accept);
        return this;
    }

    @Override
    public OAuth2Request timeout(long millisecond, Consumer<OAuth2Request> timeoutCallBack) {
        return this;
    }

    private volatile SimpleOAuth2Response auth2Response;

    protected SimpleOAuth2Response createNativeResponse(Supplier<Response> responseSupplier) {
        SimpleOAuth2Response response=  new SimpleOAuth2Response(responseSupplier.get(), convertHandler, responseJudge);


        return auth2Response =response;
    }

    protected OAuth2Response createResponse(Supplier<Response> responseSupplier) {
        createNativeResponse(responseSupplier);
        if (null != expiredCallBack) {
            //??????token????????????,?????????????????????????????????????????????token,?????????????????????
            auth2Response.judgeError(ErrorType.EXPIRED_TOKEN,() -> {

                //????????????,????????????????????????(????????????)
                expiredCallBack.call(() -> createNativeResponse(responseSupplier));

                //??????????????????response
                return auth2Response;
            });
        }
        if (null != refreshTokenExpiredCallBack) {
            //??????refresh_token????????????,?????????????????????????????????????????????token,?????????????????????
            auth2Response.judgeError(ErrorType.EXPIRED_REFRESH_TOKEN,() -> {
                //????????????,????????????????????????(????????????)
                refreshTokenExpiredCallBack.call(() -> createNativeResponse(responseSupplier));
                //??????????????????response
                return auth2Response;
            });
        }
        return auth2Response;
    }

    protected OAuth2Response createUnCheckResponse(UnCheck<Response> unCheck) {
        return createResponse(() -> UnCheck.unCheck(unCheck));
    }

    @Override
    public OAuth2Response get() {
        return createUnCheckResponse(request::get);
    }

    @Override
    public OAuth2Response put() {
        return createUnCheckResponse(request::put);
    }

    @Override
    public OAuth2Response post() {
        return createUnCheckResponse(request::post);
    }

    @Override
    public OAuth2Response delete() {
        return createUnCheckResponse(request::delete);
    }

    @Override
    public OAuth2Response patch() {
        return createUnCheckResponse(request::patch);
    }
}
