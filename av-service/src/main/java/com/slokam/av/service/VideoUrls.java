package com.slokam.av.service;

import com.slokam.av.entity.MovieMediaType;
import java.net.*;
import java.util.*;

/** Pure URL parsing: no DNS lookups, HTTP requests or downloads. */
public final class VideoUrls {
    private VideoUrls() {}
    public static String platform(String url) {
        String host = URI.create(url).getHost().toLowerCase(Locale.ROOT);
        if (Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtube-nocookie.com", "youtube-nocookie.com").contains(host)) return "YOUTUBE";
        if (Set.of("vimeo.com", "www.vimeo.com", "player.vimeo.com").contains(host)) return "VIMEO";
        return null;
    }
    public static String embed(MovieMediaType type, String url) {
        if (!type.isVideo()) return null;
        URI uri = URI.create(url);
        String platform = platform(url), id = null;
        String[] path = uri.getPath().split("/");
        if ("YOUTUBE".equals(platform)) {
            if (uri.getHost().equalsIgnoreCase("youtu.be") && path.length == 2) id = path[1];
            else if (path.length == 3 && Set.of("embed", "shorts", "live").contains(path[1])) id = path[2];
            else if ("/watch".equals(uri.getPath()) && uri.getRawQuery() != null) {
                for (String p : uri.getRawQuery().split("&")) if (p.startsWith("v=")) id = URLDecoder.decode(p.substring(2), java.nio.charset.StandardCharsets.UTF_8);
            }
            return id != null && id.matches("[a-zA-Z0-9_-]{11}") ? "https://www.youtube-nocookie.com/embed/" + id : null;
        }
        if ("VIMEO".equals(platform)) {
            if (path.length == 2) id = path[1];
            else if (path.length == 3 && "video".equals(path[1])) id = path[2];
            // Private/unlisted video tokens are not guessed or discarded into a broken embed.
            return id != null && id.matches("[0-9]+") && uri.getRawQuery() == null ? "https://player.vimeo.com/video/" + id : null;
        }
        return null;
    }
}
