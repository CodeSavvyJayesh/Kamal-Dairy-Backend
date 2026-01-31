package com.kamaldairy.kamal_dairy_backend.dto;

public class SignupRequest {
    private String name;
    private String email;
    private String password;

    public SignupRequest(){

    }
    public String getName(){
        return name;
    }
    public String getEmail(){
        return email;

    }
    public String getPassword(){
        return password;
    }
    public void setName(String name)
    {
        this.name = name;
    }
    public void setEmail(String email)
    {
        this.email = email;

    }
    public void setPassword(String password )
    {
         this.password = password;
    }

}
