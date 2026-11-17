package com.slokam.av.controller;

import com.slokam.av.service.MovieInterestService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/movies/{movieId}/interest")
public class MovieInterestController {
    private final MovieInterestService interests;

    public MovieInterestController(MovieInterestService interests) {
        this.interests = interests;
    }

    @GetMapping
    public MovieInterestService.InterestState get(@PathVariable String movieId, Principal principal) {
        return interests.get(movieId, principal);
    }

    @PostMapping
    public MovieInterestService.InterestState add(@PathVariable String movieId, Principal principal) {
        return interests.add(movieId, principal);
    }

    @DeleteMapping
    public MovieInterestService.InterestState remove(@PathVariable String movieId, Principal principal) {
        return interests.remove(movieId, principal);
    }
}
