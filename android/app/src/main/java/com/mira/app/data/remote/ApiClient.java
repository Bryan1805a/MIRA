package com.mira.app.data.remote;

import android.content.Context;

import com.google.gson.Gson;
import com.mira.app.BuildConfig;
import com.mira.app.data.session.AuthInterceptor;
import com.mira.app.data.session.TokenAuthenticator;
import com.mira.app.data.session.TokenStore;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {

    private static volatile MiraApi api;

    private ApiClient() {
    }

    public static MiraApi api(Context context) {
        MiraApi local = api;

        if (local == null) {
            synchronized (ApiClient.class) {
                local = api;

                if (local == null) {
                    Context appContext =
                            context.getApplicationContext();

                    TokenStore tokenStore = TokenStore.get(appContext);
                    Gson gson = new Gson();

                    OkHttpClient refreshClient =
                            createHttpClient();

                    MiraApi refreshApi = new Retrofit.Builder()
                            .baseUrl(BuildConfig.API_BASE_URL)
                            .client(refreshClient)
                            .addConverterFactory(
                                    GsonConverterFactory.create(gson)
                            )
                            .build()
                            .create(MiraApi.class);

                    OkHttpClient authenticatedClient =
                            createHttpClient()
                                    .newBuilder()
                                    .addInterceptor(
                                            new AuthInterceptor(tokenStore)
                                    )
                                    .authenticator(
                                            new TokenAuthenticator(
                                                    tokenStore,
                                                    refreshApi
                                            )
                                    )
                                    .build();

                    local = new Retrofit.Builder()
                            .baseUrl(BuildConfig.API_BASE_URL)
                            .client(authenticatedClient)
                            .addConverterFactory(
                                    GsonConverterFactory.create(gson)
                            )
                            .build()
                            .create(MiraApi.class);

                    api = local;
                }
            }
        }

        return local;
    }

    private static OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
    }
}