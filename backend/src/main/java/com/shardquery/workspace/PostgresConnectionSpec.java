package com.shardquery.workspace;

import com.shardquery.web.ApiException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public record PostgresConnectionSpec(
    String host, int port, String database, String user, String password, String sslmode) {
  @Override
  public String toString() {
    return "PostgresConnectionSpec[credentials redacted]";
  }

  public static PostgresConnectionSpec parse(String link, Set<String> allowed) {
    try {
      URI u = new URI(link);
      if (!Set.of("postgres", "postgresql").contains(u.getScheme())
          || u.getHost() == null
          || u.getFragment() != null
          || u.getRawUserInfo() == null) throw new IllegalArgumentException();
      String host = u.getHost().toLowerCase(Locale.ROOT);
      if (!allowed.contains(host))
        throw new ApiException(
            400,
            "Connection host is not enabled. Add its exact hostname to EXTERNAL_DB_ALLOWED_HOSTS in"
                + " .env and restart the app.");
      int port = u.getPort() == -1 ? 5432 : u.getPort();
      if (port < 1 || port > 65535) throw new IllegalArgumentException();
      String[] auth = u.getRawUserInfo().split(":", 2);
      if (auth.length != 2) throw new IllegalArgumentException();
      String user = decode(auth[0]),
          password = decode(auth[1]),
          database = decode(u.getRawPath().substring(1));
      if (user.isBlank()
          || user.length() > 128
          || password.isEmpty()
          || password.length() > 1024
          || !database.matches("[A-Za-z0-9_-]{1,63}")
          || user.indexOf('\0') >= 0
          || password.indexOf('\0') >= 0) throw new IllegalArgumentException();
      String ssl = "verify-full";
      if (u.getRawQuery() != null) {
        String[] q = u.getRawQuery().split("=", -1);
        if (q.length != 2
            || !q[0].equals("sslmode")
            || !Set.of("verify-full", "require", "disable").contains(q[1]))
          throw new IllegalArgumentException();
        ssl = q[1];
      }
      if (ssl.equals("disable")
          && !Set.of(
                  "localhost",
                  "127.0.0.1",
                  "host.docker.internal",
                  "product-db",
                  "customer-db",
                  "order-db")
              .contains(host))
        throw new ApiException(
            400,
            "Remote database connections require TLS. Use sslmode=verify-full, or require if your"
                + " provider explicitly requires it.");
      return new PostgresConnectionSpec(host, port, database, user, password, ssl);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(
          400,
          "Use a PostgreSQL connection link:"
              + " postgresql://user:password@host:5432/database?sslmode=verify-full. Percent-encode"
              + " special characters. Only sslmode is accepted as a URL option.");
    }
  }

  private static String decode(String value) {
    return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  public String jdbcUrl() {
    return "jdbc:postgresql://" + host + ":" + port + "/" + database;
  }
}
