package com.onlinejudge.leaderboard.api;

import com.onlinejudge.leaderboard.dto.LeaderboardEntryResponse;
import com.onlinejudge.leaderboard.application.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping("/problems")
    public ResponseEntity<List<LeaderboardEntryResponse>> getProblemLeaderboard() {
        return ResponseEntity.ok(leaderboardService.getProblemLeaderboard());
    }
}

