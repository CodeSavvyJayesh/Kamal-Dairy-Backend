package com.kamaldairy.kamal_dairy_backend.dto;

public class LoginResponse {
    private String token;
    public LoginResponse(String token)
    {
        this.token = token;
    }
    public String getToken()
    {
        return token;
    }
}
