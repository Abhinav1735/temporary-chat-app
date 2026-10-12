package com.tempchat.server.config;

import com.tempchat.server.entity.Role;
import com.tempchat.server.entity.User;
import com.tempchat.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Value("${app.initial-admin.login-id}")
    private String adminLoginId;

    @Value("${app.initial-admin.password}")
    private String adminPassword;

    @Bean
    CommandLineRunner createInitialAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            if (!userRepository.existsByRole(Role.ADMIN)) {

                User admin = new User(
                        adminLoginId,
                        passwordEncoder.encode(adminPassword),
                        Role.ADMIN
                );

                admin.setMustChangeCredentials(false);

                userRepository.save(admin);

                System.out.println(
                        "Initial admin account created."
                );
            }
        };
    }
}