package com.agentguard.auth;

import com.agentguard.security.JwtUtil;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtUtil jwt;

  public AuthController(UserRepository users, PasswordEncoder encoder, JwtUtil jwt) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
  }

  public record RegisterRequest(@NotBlank String username,
                                @NotBlank String password,
                                @NotBlank String role) {}
  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
    if (!java.util.Set.of("ADMIN", "OPERATOR", "AUDITOR").contains(req.role())) {
      return ResponseEntity.badRequest().body(Map.of("error", "invalid role"));
    }
    if (users.findByUsername(req.username()).isPresent()) {
      return ResponseEntity.badRequest().body(Map.of("error", "username taken"));
    }
    User u = new User(req.username(), encoder.encode(req.password()), req.role());
    users.save(u);
    return ResponseEntity.ok(Map.of("token", jwt.generate(u.getUsername(), u.getRole())));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest req) {
    return users.findByUsername(req.username())
        .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
        .map(u -> ResponseEntity.ok(Map.of("token", jwt.generate(u.getUsername(), u.getRole()))))
        .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "bad credentials")));
  }
}
