package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;

@Entity   // here enity is representing that we have mapped this class with java database
@Table(name = "users") // table name is uses as of now !
// now the problem in this class is : not the problem but we have not used the lombok notations so we are not able to
// use the annotations like noargs constuctor and allargs contsitruct
// we could have reduce the boiler plate code so much by doing this much
public class User {
    @Id    //primary key is set right now !
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // automatic incrementation of id
    private Long id;   // id is in Long
    @Column(name = "full_name", nullable = false)
    private String name;
    @Column(nullable = false, unique = true)
    private String email;   // we cant keep this empty  // we have to make sure that we are setting unique values
    @Column(nullable = false)   // here nullable is imp... but we have to make sure that passowrd cant be unique
    private String password;
    public String role;             // now it should be obvious that as we craete the account it should be user
    private boolean enabled;
    private String otp;
    private LocalDateTime otpExpiry;

    // this is required by the JPA
     // this is noarg constructor

    public User(){

    }
    // all args constructor

    public User(String name,String email,String password)
    {
         this.name = name;
         this.email = email;
         this.password = password;

    }

    // getters and setters are defined here !

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
