package com.kamaldairy.kamal_dairy_backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String fullName;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String password;
    // this is required by the JPA
    public User(){

    }
    public User(String fullName,String email,String password)
    {
         this.fullName = fullName;
         this.email = email;
         this.password = password;

    }
    public Long getId(){
        return id;

    }

    public String getFullName(){
        return fullName;
    }

    public void setFullName(){
        this.fullName = fullName;
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


}
