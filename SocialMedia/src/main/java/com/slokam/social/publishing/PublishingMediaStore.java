package com.slokam.social.publishing;

import java.io.*;
import java.nio.file.*;
import org.springframework.core.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PublishingMediaStore {
  private final PublishingProperties properties;
  public PublishingMediaStore(PublishingProperties p) { properties = p; }

  public Path path(String id) {
    if (!id.matches("[a-f0-9-]{36}"))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return properties.getStorageDirectory().toAbsolutePath().normalize().resolve(id);
  }

  public void save(String id, MultipartFile file) {
    Path target = path(id);
    try {
      Files.createDirectories(target.getParent());
      try (var input = file.getInputStream(); var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
        byte[] buffer = new byte[65536];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
          total += read;
          if (total > properties.getMaxMediaBytes())
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Media exceeds configured limit");
          output.write(buffer, 0, read);
        }
        if (total != file.getSize()) throw new IOException("Size mismatch");
      }
    } catch (IOException | RuntimeException e) {
      delete(id);
      if (e instanceof ResponseStatusException response) throw response;
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store media");
    }
  }

  public Resource resource(String id) {
    var resource = new FileSystemResource(path(id));
    if (!resource.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found");
    return resource;
  }

  public void delete(String id) {
    try { Files.deleteIfExists(path(id)); } catch (IOException ignored) { /* orphan cleanup is operational */ }
  }

  public String publicUrl(String id) {
    String base = properties.getPublicBaseUrl();
    try {
      var uri = java.net.URI.create(base);
      if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
          || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
    } catch (RuntimeException e) {
      throw new PublishFailure("ACTION_REQUIRED", "Configure PUBLISHING_PUBLIC_BASE_URL (social.publishing.public-base-url) as a public HTTPS URL reachable by Facebook and Instagram");
    }
    return base.replaceAll("/+$", "") + properties.getPublicMediaPath() + id + "/media";
  }
}
