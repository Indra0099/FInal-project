package com.shardquery.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LocalGuard extends OncePerRequestFilter {
  private final AtomicInteger writes = new AtomicInteger();
  private final java.util.Map<String, Integer> authAttempts = new java.util.HashMap<>();

  @org.springframework.beans.factory.annotation.Value("${app.write-limit:60}")
  private int writeLimit;

  private volatile long window = System.currentTimeMillis();

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("X-Frame-Options", "DENY");
    res.setHeader("Referrer-Policy", "same-origin");
    if (req.getRequestURI().startsWith("/api/")) {
      String origin = req.getHeader("Origin");
      if (origin != null) {
        try {
          var uri = URI.create(origin);
          String host = uri.getHost();
          if (!"http".equals(uri.getScheme())
              || !("localhost".equals(host) || "127.0.0.1".equals(host))
              || !(uri.getPort() == req.getServerPort() || uri.getPort() == 5178)) {
            reject(res, 403, "This local workspace does not accept requests from that origin.");
            return;
          }
        } catch (Exception e) {
          reject(res, 403, "Invalid origin.");
          return;
        }
      }
      if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())) {
        synchronized (this) {
          if (System.currentTimeMillis() - window > 60000) {
            window = System.currentTimeMillis();
            writes.set(0);
            authAttempts.clear();
          }
        }
        if (req.getRequestURI().equals("/api/auth/login")
            || req.getRequestURI().equals("/api/auth/register")) {
          synchronized (this) {
            if (authAttempts.size() > 1000
                || authAttempts.merge(req.getRemoteAddr(), 1, Integer::sum) > 15) {
              reject(res, 429, "Too many sign-in attempts. Wait a minute and try again.");
              return;
            }
          }
        }
        if (writes.incrementAndGet() > writeLimit) {
          reject(res, 429, "Too many requests. Wait a minute and retry.");
          return;
        }
      }
    }
    chain.doFilter(req, res);
  }

  private void reject(HttpServletResponse r, int status, String message) throws IOException {
    r.setStatus(status);
    r.setContentType("application/json");
    r.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
  }
}
