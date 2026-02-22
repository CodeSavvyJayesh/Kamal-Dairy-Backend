package com.kamaldairy.kamal_dairy_backend.service;
import com.kamaldairy.kamal_dairy_backend.dto.LoginRequest;
import com.kamaldairy.kamal_dairy_backend.dto.SignupRequest;
import com.kamaldairy.kamal_dairy_backend.model.User;
import com.kamaldairy.kamal_dairy_backend.repository.UserRepository;
import com.kamaldairy.kamal_dairy_backend.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    public User registerUser(SignupRequest request)
    {
        // if duplicate email
        if(userRepository.existsByEmail(request.getEmail()))
        {
            throw new RuntimeException("Email already Registered ");
        }
        // create new user
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());

        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        user.setRole("ROLE_USER");
        return userRepository.save(user);
    }

    // login
    public String login(LoginRequest request)
    {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new RuntimeException("User not found"));
        if(!passwordEncoder.matches(request.getPassword(),user.getPassword())){
            throw new RuntimeException("Invalid Password");
        }
        return JwtUtil.generateToken(user.getEmail(),user.getRole());
    }
}
