package com.slokam.av.service;

import com.slokam.av.dto.*;
import com.slokam.av.entity.*;
import com.slokam.av.repository.*;
import com.slokam.av.mapper.CatalogMapper;
import com.slokam.av.exception.custom.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class MovieMediaService {
    private final MovieRepository movies;
    private final MovieMediaRepository media;
    private final CatalogMapper mapper;
    public MovieMediaService(MovieRepository movies, MovieMediaRepository media, CatalogMapper mapper) { this.movies = movies; this.media = media; this.mapper = mapper; }
    public Movie lock(String id) { return movies.lockById(id).orElseThrow(() -> new ApiException(404, "MOVIE_NOT_FOUND", "Movie not found")); }
    public List<MovieMediaResponse> list(String id) {
        if (!movies.existsById(id)) throw new ApiException(404, "MOVIE_NOT_FOUND", "Movie not found");
        return media.findByMovieIdOrderByDisplayOrderAscIdAsc(id).stream().map(mapper::media).toList();
    }
    @Transactional public MovieMediaResponse add(String id, MovieMediaRequest r) {
        Movie m = lock(id); var entry = new MovieMedia(); entry.movieId = id; entry.displayOrder = media.findByMovieIdOrderByDisplayOrderAscIdAsc(id).stream().mapToInt(x -> x.displayOrder).max().orElse(-1) + 1; apply(entry, r); entry = media.save(entry); sync(m); return mapper.media(entry);
    }
    @Transactional public MovieMediaResponse update(String id, String mediaId, MovieMediaRequest r) {
        Movie m = lock(id); var entry = find(id, mediaId); apply(entry, r); media.flush(); sync(m); return mapper.media(entry);
    }
    private MovieMedia find(String id, String mediaId) { return media.findById(mediaId).filter(x -> x.movieId.equals(id)).orElseThrow(() -> new ApiException(404, "MEDIA_NOT_FOUND", "Movie media not found")); }
    private void apply(MovieMedia e, MovieMediaRequest r) {
        if (r.isPrimary && !r.mediaType.isPoster()) throw CatalogValues.invalid("Only a poster can be the primary poster");
        String platform = VideoUrls.platform(r.mediaUrl);
        if (platform != null && !r.mediaType.isVideo() && r.mediaType != MovieMediaType.OTHER) throw CatalogValues.invalid("Video platform URLs require a video media type");
        if (platform != null && CatalogValues.text(r.sourcePlatform) != null && !platform.equalsIgnoreCase(r.sourcePlatform.trim())) throw CatalogValues.invalid("sourcePlatform does not match the video URL");
        if (r.isPrimary) clearPrimary(e.movieId, e.id);
        e.mediaType = r.mediaType; e.title = CatalogValues.text(r.title); e.description = CatalogValues.text(r.description);
        e.mediaUrl = r.mediaUrl; e.thumbnailUrl = CatalogValues.text(r.thumbnailUrl); e.language = CatalogValues.text(r.language);
        e.isOfficial = r.isOfficial; e.isPrimary = r.isPrimary; if (r.displayOrder != null) e.displayOrder = r.displayOrder;
        e.sourcePlatform = platform == null ? CatalogValues.text(r.sourcePlatform) : platform; e.publishedAt = r.publishedAt;
    }
    private void clearPrimary(String movieId, String except) {
        for (var x : media.findByMovieIdOrderByDisplayOrderAscIdAsc(movieId)) if (!x.id.equals(except) && x.isPrimary) x.isPrimary = false;
        media.flush(); // Release the unique primary slot before assigning it to another record.
    }
    @Transactional public MovieMediaResponse primary(String id, String mediaId) {
        Movie m = lock(id); var e = find(id, mediaId);
        if (!e.mediaType.isPoster()) throw CatalogValues.invalid("Only a poster can be the primary poster");
        clearPrimary(id, e.id); e.isPrimary = true; media.flush(); sync(m); return mapper.media(e);
    }
    @Transactional public List<MovieMediaResponse> reorder(String id, List<String> ids) {
        Movie m = lock(id); var all = media.findByMovieIdOrderByDisplayOrderAscIdAsc(id);
        Set<String> supplied = new HashSet<>(ids);
        if (supplied.size() != ids.size() || !supplied.equals(new HashSet<>(all.stream().map(x -> x.id).toList())))
            throw CatalogValues.invalid("mediaIds must contain every media ID for this movie exactly once");
        Map<String, Integer> order = new HashMap<>(); for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);
        all.forEach(x -> x.displayOrder = order.get(x.id)); media.flush(); sync(m); return list(id);
    }
    @Transactional public void delete(String id, String mediaId) { Movie m = lock(id); media.delete(find(id, mediaId)); media.flush(); sync(m); }

    /** Legacy columns are projections for booking/email code and old clients, not independent media sources. */
    public void sync(Movie m) {
        var all = media.findByMovieIdOrderByDisplayOrderAscIdAsc(m.id);
        var posters = all.stream().filter(x -> x.mediaType.isPoster()).toList();
        if (!posters.isEmpty() && posters.stream().noneMatch(x -> x.isPrimary)) { posters.getFirst().isPrimary = true; media.flush(); }
        m.posterUrl = posters.stream().filter(x -> x.isPrimary).map(x -> x.mediaUrl).findFirst().orElse(null);
        m.posterImages.clear(); m.posterImages.addAll(posters.stream().map(x -> x.mediaUrl).distinct().toList());
        m.trailerUrl = all.stream().filter(x -> x.mediaType == MovieMediaType.TRAILER || x.mediaType == MovieMediaType.TRAILER_VERSION).map(x -> x.mediaUrl).findFirst().orElse(null);
        m.backdropUrl = all.stream().filter(x -> x.mediaType == MovieMediaType.STILL).map(x -> x.mediaUrl).findFirst().orElse(null);
        m.updatedAt = java.time.Instant.now();
    }
    // Old fields route through the same media model. Explicit [] clears posters; omitted fields preserve them.
    public void applyLegacy(Movie m, MovieRequest r) {
        if (r.posterImages != null || r.posterUrl != null || r.supplied("posterImages") || r.supplied("posterUrl")) {
            List<String> desired = CatalogValues.distinct(r.posterImages);
            String primary = CatalogValues.text(r.posterUrl);
            if (primary != null) { desired.remove(primary); desired.addFirst(primary); }
            var existing = media.findByMovieIdOrderByDisplayOrderAscIdAsc(m.id);
            var posters = existing.stream().filter(x -> x.mediaType.isPoster()).toList();
            // Removing a legacy list entry removes that poster, but preserves all video and still records.
            for (var e : posters) if (!desired.contains(e.mediaUrl)) media.delete(e);
            for (var e : posters) e.isPrimary = false;
            media.flush();
            for (int i = 0; i < desired.size(); i++) {
                String url = desired.get(i);
                if (VideoUrls.platform(url) != null) throw CatalogValues.invalid("Poster URLs cannot point to a video platform");
                var e = posters.stream().filter(x -> x.mediaUrl.equals(url)).findFirst().orElseGet(MovieMedia::new);
                e.movieId = m.id; if (e.mediaType == null) e.mediaType = MovieMediaType.POSTER;
                e.mediaUrl = url; e.displayOrder = i; e.isPrimary = i == 0; media.save(e);
            }
        }
        if (r.trailerUrl != null || r.supplied("trailerUrl")) legacySingle(m.id, r.trailerUrl, MovieMediaType.TRAILER);
        if (r.backdropUrl != null || r.supplied("backdropUrl")) {
            if (CatalogValues.text(r.backdropUrl) != null && VideoUrls.platform(r.backdropUrl) != null) throw CatalogValues.invalid("Backdrop URLs cannot point to a video platform");
            legacySingle(m.id, r.backdropUrl, MovieMediaType.STILL);
        }
        media.flush();
        if (r.posterImages != null || r.posterUrl != null || r.supplied("posterImages") || r.supplied("posterUrl") || r.trailerUrl != null || r.backdropUrl != null || r.supplied("trailerUrl") || r.supplied("backdropUrl")) sync(m);
    }
    private void legacySingle(String movieId, String url, MovieMediaType type) {
        var existing = media.findByMovieIdOrderByDisplayOrderAscIdAsc(movieId).stream().filter(x -> x.mediaType == type || (type == MovieMediaType.TRAILER && x.mediaType == MovieMediaType.TRAILER_VERSION)).findFirst();
        if (CatalogValues.text(url) == null) { existing.ifPresent(media::delete); return; }
        var e = existing.orElseGet(MovieMedia::new); e.movieId = movieId; e.mediaType = type; e.mediaUrl = url;
        e.sourcePlatform = VideoUrls.platform(url); media.save(e);
    }
}
