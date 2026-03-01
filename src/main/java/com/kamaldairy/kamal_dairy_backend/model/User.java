package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "full_name", nullable = false)
    private String name;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String password;
    public String role;
    private boolean enabled;
    private String otp;
    private LocalDateTime otpExpiry;

    // this is required by the JPA
    public User(){

    }
    public User(String name,String email,String password)
    {
         this.name = name;
         this.email = email;
         this.password = password;

    }
    public Long getId(){
        return id;

    }

    public String getName(){
       return name;
    }

    public void setName(String name){
        this.name = name;
    }
    public String getEmail(){
        return email;
    }
    public void setEmail(String email)
    {
         this.email=email;
    }
    public String getPassword(){

        return password;
    }
    public void setPassword(String password)
    {
        this.password=password;
    }

    // role getter / setter
    public String getRole()
    {
        return role;
    }
    public void setRole(String role)
    {
        this.role = role;
    }

    public boolean isEnabled()
    {
        return enabled;
    }
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
    public String getOtp(){
        return otp;
    }
    public void setOtp(String otp){
        this.otp = otp;
    }

    public LocalDateTime getOtpExpiry(){
        return otpExpiry;
    }
    public void setOtpExpiry(LocalDateTime otpExpiry)
    {
        this.otpExpiry = otpExpiry;
    }

}
