package com.mira.app.data.model;

public class RegisterRequest {

    private final String email;
    private final String password;

    public RegisterRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}