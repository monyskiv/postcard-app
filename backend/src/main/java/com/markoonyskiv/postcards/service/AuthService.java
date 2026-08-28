package com.markoonyskiv.postcards.service;

import com.markoonyskiv.postcards.model.Admin;
import com.markoonyskiv.postcards.repository.AdminRepository;
import com.markoonyskiv.postcards.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AdminRepository adminRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String login(String email, String password) {
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return jwtService.generateToken(admin.getEmail());
    }
}
