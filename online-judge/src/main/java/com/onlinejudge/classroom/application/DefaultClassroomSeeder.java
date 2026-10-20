package com.onlinejudge.classroom.application;

import com.onlinejudge.classroom.domain.ClassGroup;
import com.onlinejudge.classroom.persistence.ClassGroupRepository;
import com.onlinejudge.shared.security.SchoolSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DefaultClassroomSeeder implements CommandLineRunner {

    static final String DEFAULT_CLASS_NAME = "默认班级";

    private final ClassGroupRepository classGroupRepository;
    private final SchoolSecurityProperties securityProperties;

    @Override
    @Transactional
    public void run(String... args) {
        if (!securityProperties.teacherDevAutoAuth()) {
            return;
        }
        classGroupRepository.findByNameIgnoreCase(DEFAULT_CLASS_NAME)
                .or(() -> classGroupRepository.findAllByOrderByCreatedAtDesc().stream().findFirst())
                .orElseGet(() -> classGroupRepository.save(ClassGroup.builder()
                        .name(DEFAULT_CLASS_NAME)
                        .teacherName("开发教师")
                        .build()));
    }
}
