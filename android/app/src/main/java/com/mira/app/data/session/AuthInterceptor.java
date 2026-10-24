package com.mira.app.data.session;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";

    private final TokenStore tokenStore;

    public AuthInterceptor(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String accessToken = tokenStore.accessToken();

        if (accessToken == null
                || accessToken.isBlank()
                || request.header(AUTHORIZATION) != null) {
            return chain.proceed(request);
        }

        Request authenticated = request.newBuilder()
                .header(AUTHORIZATION, BEARER + accessToken)
                .build();

        return chain.proceed(authenticated);
    }
}