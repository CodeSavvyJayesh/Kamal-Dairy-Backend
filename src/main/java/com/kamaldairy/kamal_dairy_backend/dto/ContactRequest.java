package com.kamaldairy.kamal_dairy_backend.dto;

public class ContactRequest {
    // this class will consist logic of what type of request is coming from the frontend
    private String name;
    private String email;
    private String phone;
    private String message;

    // this constructor is must
    public ContactRequest(){

    }
    // make getters and setters
    public String getName(){
        return name;
    }
    public void setName(String name)
    {
        this.name = name;
    }
    public String getEmail()
    {
        return email;
    }
    public void setEmail(String email)
    {
        this.email = email;
    }
    public String getPhone()
    {
        return phone;
    }
    public void setPhone(String phone)
    {
        this.phone = phone;
    }
    public String getMessage()
    {
        return message;
    }
    public void setMessage(String message)
    {
        this.message = message;
    }
}
