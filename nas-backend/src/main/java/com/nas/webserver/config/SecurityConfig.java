
package com.nas.webserver.config;

import com.nas.webserver.service.CustomUserDetailsService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // Encrypt user passwords
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Authenticate users using the database
    @Bean
    public AuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    // Configure authentication and access permissions
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            .authorizeHttpRequests(auth -> auth

                // Public pages and resources
                .requestMatchers(
                        "/",
                        "/index.html",
                        "/register.html",
                        "/login",
                        "/login.html",
                        "/error",
                        "/api/csrf",
                        "/css/**",
                        "/js/**"
                ).permitAll()

                // Allow new users to register
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/users/register"
                ).permitAll()

                // Only administrators can access backups
                .requestMatchers(
                        "/api/backups",
                        "/api/backups/**",
                        "/backups.html"
                ).hasRole("ADMIN")

                // Only administrators can manage file metadata
                .requestMatchers(
                        "/api/file-metadata",
                        "/api/file-metadata/**"
                ).hasRole("ADMIN")

                // Only administrators can access monitoring
                .requestMatchers(
                        "/dashboard.html",
                        "/monitoring.html",
                        "/api/monitoring",
                        "/api/monitoring/**"
                ).hasRole("ADMIN")

                .requestMatchers("/admin-users.html").hasRole("ADMIN")

                // Only administrators can manage users
                .requestMatchers("/api/users/**")
                .hasRole("ADMIN")

                // Allow signed-in users to view access denied page
                .requestMatchers("/access-denied.html")
                .authenticated()

                // All other pages require login
                .anyRequest().authenticated()
            )

            // Show custom page instead of 403 Whitelabel error
            .exceptionHandling(exceptions -> exceptions
                .accessDeniedPage("/access-denied.html")
            )

            // Login configuration
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/files.html", true)
                .failureUrl("/login.html?error")
                .permitAll()
            )

            // Logout configuration
            .logout(logout -> logout
                .logoutUrl("/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessUrl("/login.html?logout")
                .permitAll()
            );

        return http.build();
    }
}
