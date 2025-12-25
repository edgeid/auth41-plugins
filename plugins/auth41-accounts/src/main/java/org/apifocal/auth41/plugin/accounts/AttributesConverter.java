package org.apifocal.auth41.plugin.accounts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * JPA converter for Map<String, Object> attributes stored as JSON.
 */
@Converter
public class AttributesConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final Logger logger = Logger.getLogger(AttributesConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            logger.error("Failed to convert attributes to JSON", e);
            return "{}";
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(dbData, TYPE_REF);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse JSON attributes: " + dbData, e);
            return new HashMap<>();
        }
    }
}
