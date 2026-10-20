package com.onlinejudge.shared.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList());
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = OBJECT_MAPPER.readValue(dbData, STRING_LIST_TYPE);
            return values == null ? List.of() : values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
