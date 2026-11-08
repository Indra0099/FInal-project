package com.shardquery.auth;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  HttpSessionSecurityContextRepository contexts() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  HttpSessionCsrfTokenRepository csrfTokens() {
    return new HttpSessionCsrfTokenRepository();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      HttpSessionSecurityContextRepository contexts,
      HttpSessionCsrfTokenRepository tokens)
      throws Exception {
    return http.csrf(c -> c.csrfTokenRepository(tokens))
        .securityContext(c -> c.securityContextRepository(contexts).requireExplicitSave(true))
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/auth/register")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((q, r, x) -> json(r, 401, "Sign in to continue."))
                    .accessDeniedHandler(
                        (q, r, x) ->
                            json(
                                r,
                                403,
                                "The request could not be verified. Refresh the page and try"
                                    + " again.")))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("SHARDQUERY_SESSION")
                    .logoutSuccessHandler((q, r, a) -> r.setStatus(204)))
        .build();
  }

  private static void json(jakarta.servlet.http.HttpServletResponse r, int status, String message)
      throws java.io.IOException {
    r.setStatus(status);
    r.setContentType("application/json");
    r.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
  }
}
