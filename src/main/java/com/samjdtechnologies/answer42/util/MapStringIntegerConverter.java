package com.samjdtechnologies.answer42.util;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for storing Map<String, Integer> as JSON text in database.
 */
@Converter
public class MapStringIntegerConverter implements AttributeConverter<Map<String, Integer>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Integer> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert map to JSON", e);
        }
    }

    @Override
    public Map<String, Integer> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            TypeReference<Map<String, Integer>> typeRef = new TypeReference<Map<String, Integer>>() {};
            return objectMapper.readValue(dbData, typeRef);
        } catch (JsonProcessingException e) {
            // Fallback: try to parse as simple key=value pairs
            return parseSimpleFormat(dbData);
        }
    }

    /**
     * Fallback parser for simple key=value format.
     */
    private Map<String, Integer> parseSimpleFormat(String dbData) {
        Map<String, Integer> result = new HashMap<>();
        
        try {
            String[] pairs = dbData.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    Integer value = Integer.parseInt(keyValue[1].trim());
                    result.put(key, value);
                }
            }
        } catch (Exception e) {
            // Return empty map if parsing fails
            return new HashMap<>();
        }
        
        return result;
    }
}
