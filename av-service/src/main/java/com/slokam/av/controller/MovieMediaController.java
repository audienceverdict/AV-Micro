package com.slokam.av.controller;
import com.slokam.av.dto.*;
import com.slokam.av.service.MovieMediaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Movie Media")
@RestController
@RequestMapping("/api/v1/movies/{movieId}/media")
public class MovieMediaController {
    private final MovieMediaService service;
    public MovieMediaController(MovieMediaService service) { this.service = service; }
    @io.swagger.v3.oas.annotations.Operation(summary = "List movie media in display order", description = "List movie media in display order.")
    @GetMapping public List<MovieMediaResponse> list(@PathVariable String movieId) { return service.list(movieId); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Add movie media with a valid HTTP/HTTPS URL", description = "Add movie media with a valid HTTP/HTTPS URL.")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MovieMediaResponse add(@PathVariable String movieId, @Valid @RequestBody MovieMediaRequest r) { return service.add(movieId, r); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Update a movie media item", description = "Update a movie media item.")
    @PutMapping("/{mediaId}") public MovieMediaResponse update(@PathVariable String movieId, @PathVariable String mediaId, @Valid @RequestBody MovieMediaRequest r) { return service.update(movieId, mediaId, r); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Reorder movie media using the ordered mediaIds list", description = "Reorder movie media using the ordered mediaIds list.")
    @PutMapping("/order") public List<MovieMediaResponse> order(@PathVariable String movieId, @Valid @RequestBody MediaOrderRequest r) { return service.reorder(movieId, r.mediaIds); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Make a media item primary within its media type", description = "Make a media item primary within its media type.")
    @PutMapping("/{mediaId}/primary") public MovieMediaResponse primary(@PathVariable String movieId, @PathVariable String mediaId) { return service.primary(movieId, mediaId); }
    @io.swagger.v3.oas.annotations.Operation(summary = "Delete a movie media item", description = "Delete a movie media item.")
    @DeleteMapping("/{mediaId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String movieId, @PathVariable String mediaId) { service.delete(movieId, mediaId); }
}
