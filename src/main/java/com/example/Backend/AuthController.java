package com.example.Backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already taken!");
        }
        userRepository.save(user);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User loginDetails) {
        Optional<User> userOpt = userRepository.findByUsername(loginDetails.getUsername());

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Check password (simple check for now)
            if (user.getPassword().equals(loginDetails.getPassword())) {
                // Return user info excluding password
                Map<String, String> response = new HashMap<>();
                response.put("username", user.getUsername());
                response.put("groupName", user.getGroupName());
                return ResponseEntity.ok(response);
            }
        }
        return ResponseEntity.status(401).body("Invalid Username or Password");
    }
}