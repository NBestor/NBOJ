package com.onlinejudge.classroom.dto;

import com.onlinejudge.classroom.domain.StudentProfile;
import jakarta.validation.constraints.NotNull;

public record UpdateRosterStatusRequest(@NotNull StudentProfile.RosterStatus status) { }
