package com.shardquery.workspace;

import com.shardquery.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Installation-local encryption; the key persists on the upload volume, never in API responses. */
@Component
public class ConnectionSecrets {
  private final byte[] key;

  public ConnectionSecrets(@Value("${app.upload-dir}") String directory) throws Exception {
    Path folder = Path.of(directory).toAbsolutePath().resolve(".secrets");
    Files.createDirectories(folder);
    Path file = folder.resolve("database-connections.key");
    if (!Files.exists(file)) {
      byte[] generated = new byte[32];
      new SecureRandom().nextBytes(generated);
      try {
        Files.createFile(
            file,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      } catch (UnsupportedOperationException e) {
        Files.createFile(file);
      }
      Files.write(file, generated);
    }
    key = Files.readAllBytes(file);
    if (key.length != 32)
      throw new IllegalStateException("Invalid database connection encryption key.");
  }

  public String encrypt(UUID id, String value) {
    return crypt(id, value, true);
  }

  public String decrypt(UUID id, String value) {
    return crypt(id, value, false);
  }

  private String crypt(UUID id, String value, boolean encrypt) {
    try {
      byte[] nonce = new byte[12];
      byte[] input;
      if (encrypt) {
        new SecureRandom().nextBytes(nonce);
        input = value.getBytes(StandardCharsets.UTF_8);
      } else {
        byte[] packed = Base64.getDecoder().decode(value);
        if (packed.length < 29) throw new Exception();
        System.arraycopy(packed, 0, nonce, 0, 12);
        input = Arrays.copyOfRange(packed, 12, packed.length);
      }
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(128, nonce));
      cipher.updateAAD(id.toString().getBytes(StandardCharsets.UTF_8));
      byte[] output = cipher.doFinal(input);
      if (!encrypt) return new String(output, StandardCharsets.UTF_8);
      byte[] packed = new byte[12 + output.length];
      System.arraycopy(nonce, 0, packed, 0, 12);
      System.arraycopy(output, 0, packed, 12, output.length);
      return Base64.getEncoder().encodeToString(packed);
    } catch (Exception e) {
      throw new ApiException(
          503,
          "Connection credentials could not be opened. Restore the installation encryption key or"
              + " update the connection.");
    }
  }
}
