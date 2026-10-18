package com.agentguard.config;

import com.agentguard.security.JwtFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  private final JwtFilter jwtFilter;
  private final List<String> allowedOrigins;

  public SecurityConfig(JwtFilter jwtFilter,
      @Value("${agentguard.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
    this.jwtFilter = jwtFilter;
    this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
        .map(String::trim).filter(origin -> !origin.isEmpty()).toList();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key"));
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/**", "/api/v1/health",
                "/swagger-ui/**", "/v3/api-docs/**")
            .permitAll()
            // agent tool proxy uses API keys (Phase 2); locked down by default for now
            .requestMatchers(HttpMethod.POST, "/api/v1/tools/**")
            .permitAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/agents/**",
                "/api/v1/policies/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/decisions/**")
            .hasAnyRole("ADMIN", "OPERATOR")
            .requestMatchers("/api/v1/runs/**").hasAnyRole("ADMIN", "OPERATOR")
            .requestMatchers(HttpMethod.GET, "/api/v1/cases/**")
            .hasAnyRole("ADMIN", "OPERATOR", "AUDITOR")
            .requestMatchers("/api/v1/cases/**").hasAnyRole("ADMIN", "OPERATOR")
            .anyRequest().authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
