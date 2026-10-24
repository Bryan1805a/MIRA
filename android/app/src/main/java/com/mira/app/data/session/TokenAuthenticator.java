package com.mira.app.data.session;

import com.mira.app.data.model.RefreshRequest;
import com.mira.app.data.model.SessionResponse;
import com.mira.app.data.remote.MiraApi;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Call;

public class TokenAuthenticator implements Authenticator {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";

    private final TokenStore tokenStore;
    private final MiraApi refreshApi;
    private final Object refreshLock = new Object();

    public TokenAuthenticator(
            TokenStore tokenStore,
            MiraApi refreshApi
    ) {
        this.tokenStore = tokenStore;
        this.refreshApi = refreshApi;
    }

    @Override
    public Request authenticate(Route route, Response response) {
        if (responseCount(response) >= 2) {
            return null;
        }

        if (isAuthEndpoint(response)) {
            return null;
        }

        String authorization = response.request().header(AUTHORIZATION);

        if (authorization == null
                || !authorization.startsWith(BEARER)) {
            return null;
        }

        String failedToken = authorization.substring(BEARER.length());

        synchronized (refreshLock) {
            /*
             * Another request may have refreshed while this request waited.
             * Retry with the current token instead of refreshing again.
             */
            String currentToken = tokenStore.accessToken();

            if (currentToken != null
                    && !currentToken.equals(failedToken)) {
                return withToken(response.request(), currentToken);
            }

            String refreshToken = tokenStore.refreshToken();

            if (refreshToken == null || refreshToken.isBlank()) {
                tokenStore.clear();
                return null;
            }

            try {
                Call<SessionResponse> call = refreshApi.refresh(
                        new RefreshRequest(refreshToken)
                );

                retrofit2.Response<SessionResponse> refreshResponse =
                        call.execute();

                SessionResponse session = refreshResponse.body();

                if (!refreshResponse.isSuccessful() || session == null) {
                    tokenStore.clear();
                    return null;
                }

                tokenStore.saveSession(session);

                return withToken(
                        response.request(),
                        session.getAccessToken()
                );
            } catch (IOException exception) {
                /*
                 * Network failure: keep the session so the user can retry
                 * later. The original call still fails with 401.
                 */
                return null;
            }
        }
    }

    private Request withToken(Request request, String accessToken) {
        return request.newBuilder()
                .header(AUTHORIZATION, BEARER + accessToken)
                .build();
    }

    private boolean isAuthEndpoint(Response response) {
        String path = response.request().url().encodedPath();

        return path.endsWith("/auth/login")
                || path.endsWith("/auth/register")
                || path.endsWith("/auth/refresh");
    }

    private int responseCount(Response response) {
        int count = 1;
        Response prior = response.priorResponse();

        while (prior != null) {
            count++;
            prior = prior.priorResponse();
        }

        return count;
    }
}