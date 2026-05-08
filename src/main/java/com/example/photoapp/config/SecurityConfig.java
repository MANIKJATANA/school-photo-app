package com.example.photoapp.config;

import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.security.PhotoAppAuthEntryPoint;
import com.example.photoapp.security.jwt.JwtAuthFilter;
import com.example.photoapp.security.jwt.JwtIssuer;
import com.example.photoapp.security.jwt.JwtProperties;
import com.example.photoapp.security.jwt.JwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtProperties jwtProperties(
            @Value("${photoapp.jwt.secret}") String secret,
            @Value("${photoapp.jwt.issuer}") String issuer,
            @Value("${photoapp.jwt.access-token-ttl-seconds}") long accessTtlSeconds,
            @Value("${photoapp.jwt.refresh-token-ttl-seconds}") long refreshTtlSeconds) {
        return new JwtProperties(secret, issuer,
                Duration.ofSeconds(accessTtlSeconds),
                Duration.ofSeconds(refreshTtlSeconds));
    }

    @Bean
    public JwtIssuer jwtIssuer(JwtProperties props, Clock clock) {
        return new JwtIssuer(props, clock);
    }

    @Bean
    public JwtVerifier jwtVerifier(JwtProperties props, Clock clock) {
        return new JwtVerifier(props, clock);
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtVerifier verifier, SchoolContext schoolContext) {
        return new JwtAuthFilter(verifier, schoolContext);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthFilter jwtAuthFilter,
                                                   PhotoAppAuthEntryPoint authEntryPoint) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/api/v1/auth/login",
                                         "/api/v1/auth/refresh",
                                         "/api/v1/onboarding",
                                         "/actuator/health",
                                         "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/webhooks/**").permitAll() // ML webhook — HMAC validated in controller
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authEntryPoint))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
