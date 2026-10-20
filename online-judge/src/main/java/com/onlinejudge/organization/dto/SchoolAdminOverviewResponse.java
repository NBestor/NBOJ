package com.onlinejudge.organization.dto;

import com.onlinejudge.aiquota.application.SchoolAiQuotaService;
import com.onlinejudge.identity.dto.TeacherAccountResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.onlinejudge.aiquota.dto.TeacherAiUsageResponse;

public record SchoolAdminOverviewResponse(UUID schoolId, String schoolName,
                                          SchoolAiQuotaService.SchoolQuotaSummary quota,
                                          List<TeacherAccountResponse> teachers,
                                          Map<UUID, TeacherAiUsageResponse> teacherQuotas,
                                          long pendingApplications) { }
