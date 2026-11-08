package com.shardquery.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  private final HttpSessionSecurityContextRepository contexts;
  private final HttpSessionCsrfTokenRepository tokens;

  public AuthController(
      AuthService auth,
      HttpSessionSecurityContextRepository contexts,
      HttpSessionCsrfTokenRepository tokens) {
    this.auth = auth;
    this.contexts = contexts;
    this.tokens = tokens;
  }

  public record Registration(
      @NotBlank @Size(min = 2, max = 80) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  public AccountPrincipal me(@AuthenticationPrincipal AccountPrincipal user) {
    return user;
  }

  @PostMapping("/register")
  public ResponseEntity<AccountPrincipal> register(
      @Valid @RequestBody Registration body, HttpServletRequest req, HttpServletResponse res) {
    var user = auth.register(body.name(), body.email(), body.password());
    signIn(user, req, res);
    return ResponseEntity.status(201).body(user);
  }

  @PostMapping("/login")
  public AccountPrincipal login(
      @Valid @RequestBody Login body, HttpServletRequest req, HttpServletResponse res) {
    var user = auth.login(body.email(), body.password());
    signIn(user, req, res);
    return user;
  }

  private void signIn(AccountPrincipal user, HttpServletRequest req, HttpServletResponse res) {
    if (req.getSession(false) != null) req.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    SecurityContextHolder.setContext(context);
    tokens.saveToken(null, req, res);
    contexts.saveContext(context, req, res);
  }
}
