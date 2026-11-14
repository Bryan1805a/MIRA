package com.mira.app.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class ApiErrorsTest {

    private static final MediaType JSON =
            MediaType.get("application/json");

    @Test
    public void returnsTheServerMessage() {
        Response<?> response = Response.error(
                400,
                ResponseBody.create(
                        "{\"error\":\"invalid_request\","
                                + "\"message\":\"Bad input\","
                                + "\"requestId\":\"request-1\"}",
                        JSON
                )
        );

        assertEquals("Bad input", ApiErrors.message(response));
    }

    @Test
    public void fallsBackWhenTheBodyIsMalformed() {
        Response<?> response = Response.error(
                500,
                ResponseBody.create("not-json", JSON)
        );

        assertEquals(
                "Request failed (500).",
                ApiErrors.message(response)
        );
    }

    @Test
    public void fallsBackWhenTheMessageIsBlank() {
        Response<?> response = Response.error(
                409,
                ResponseBody.create(
                        "{\"error\":\"version_conflict\","
                                + "\"message\":\"  \"}",
                        JSON
                )
        );

        assertEquals(
                "Request failed (409).",
                ApiErrors.message(response)
        );
    }

    @Test
    public void fallsBackWhenThereIsNoErrorBody() {
        Response<?> response = Response.error(
                503,
                ResponseBody.create("", JSON)
        );

        assertEquals(
                "Request failed (503).",
                ApiErrors.message(response)
        );
    }
}
