package com.onlinejudge.learning.standardlibrary.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashSet;
import java.util.List;

@Converter
public class LongListAttributeConverter implements AttributeConverter<List<Long>, String> {

    @Override
    public String convertToDatabaseColumn(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        values.stream().filter(value -> value != null && value > 0).forEach(unique::add);
        return unique.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    @Override
    public List<Long> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            try {
                long id = Long.parseLong(part.trim());
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // 兼容旧数据或人工编辑造成的无效片段，只保留可审计的正整数提交 ID。
            }
        }
        return List.copyOf(ids);
    }
}
