package com.shardquery.auth;

import com.shardquery.persistence.*;
import com.shardquery.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final String dummyHash;

  public AuthService(UserRepository users, PasswordEncoder passwords) {
    this.users = users;
    this.passwords = passwords;
    dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  public AccountPrincipal register(String name, String email, String password) {
    if (name == null || name.strip().length() < 2)
      throw new ApiException(400, "Enter your name (2–80 characters).");
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ApiException(400, "Password must be at most 72 UTF-8 bytes.");
    String normalized = email.strip().toLowerCase(Locale.ROOT);
    var user = new UserRecord();
    user.id = UUID.randomUUID();
    user.name = name.strip();
    user.email = normalized;
    user.passwordHash = passwords.encode(password);
    user.createdAt = Instant.now();
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(409, "An account with this email already exists. Please sign in.");
    }
    return principal(user);
  }

  public AccountPrincipal login(String email, String password) {
    var user = users.findByEmail(email.strip().toLowerCase(Locale.ROOT));
    boolean matches =
        password.getBytes(StandardCharsets.UTF_8).length <= 72
            && passwords.matches(password, user.map(u -> u.passwordHash).orElse(dummyHash));
    if (!matches || user.isEmpty()) throw new ApiException(401, "Email or password is incorrect.");
    return principal(user.get());
  }

  private AccountPrincipal principal(UserRecord u) {
    return new AccountPrincipal(u.id, u.name, u.email);
  }
}
