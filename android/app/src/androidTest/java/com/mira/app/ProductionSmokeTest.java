package com.mira.app;

import static org.junit.Assert.assertNotNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.Gson;
import com.mira.app.data.model.LoginRequest;
import com.mira.app.data.model.RegisterRequest;
import com.mira.app.data.model.RegisterResponse;
import com.mira.app.data.model.SessionResponse;
import com.mira.app.data.remote.MiraApi;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Manual production smoke test. It is skipped unless the instrumentation
 * argument {@code miraProductionSmoke=true} is supplied.
 */
@RunWith(AndroidJUnit4.class)
public class ProductionSmokeTest {

    private static final String BASE_URL =
            "https://mira-backend-094w.onrender.com/";

    private MiraApi api;

    @Before
    public void setUp() {
        String enabled = InstrumentationRegistry.getArguments()
                .getString("miraProductionSmoke");

        Assume.assumeTrue(
                "Set miraProductionSmoke=true to run.",
                "true".equals(enabled)
        );

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        api = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(
                        GsonConverterFactory.create(new Gson())
                )
                .client(client)
                .build()
                .create(MiraApi.class);
    }

    @Test
    public void registerAndLoginAgainstProduction() throws Exception {
        String email = "device-smoke-" + UUID.randomUUID()
                + "@example.com";
        String password = "Correct-Horse-123!";

        RegisterResponse registered = api.register(
                new RegisterRequest(email, password)
        ).execute().body();

        assertNotNull(registered);

        SessionResponse session = api.login(
                new LoginRequest(email, password)
        ).execute().body();

        assertNotNull(session);
        assertNotNull(session.getAccessToken());
        assertNotNull(session.getRefreshToken());
    }
}
