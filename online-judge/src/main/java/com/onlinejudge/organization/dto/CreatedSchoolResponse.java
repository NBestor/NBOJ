package com.onlinejudge.organization.dto;

public record CreatedSchoolResponse(SchoolResponse school, String temporaryPassword,
                                    String schoolRegistrationCode) { }
