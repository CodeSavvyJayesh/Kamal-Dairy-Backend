package com.kamaldairy.kamal_dairy_backend.dto;

public class LoginRequest {
    private String email;
    private String password;
    public void setEmail(String email){
        this.email = email;
    }
    public void setPassword(String password)
    {
         this.password = password;

    }
    public String getEmail(){
        return email;
    }
    public String getPassword(){
         return password;
    }
}
