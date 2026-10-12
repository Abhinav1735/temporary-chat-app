package com.tempchat.server.security;

import com.tempchat.server.entity.User;
import com.tempchat.server.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId)
            throws UsernameNotFoundException {

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found"
                        )
                );

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getLoginId())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .disabled(!user.isEnabled())
                .build();
    }
}