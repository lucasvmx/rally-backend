package br.org.fju.rally.dominio;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Converter
public class ConversorJsonMapa implements AttributeConverter<Map<String, Object>, String> {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> TIPO = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> atributo) {
        return atributo == null ? "{}" : JSON.writeValueAsString(atributo);
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String valor) {
        if (valor == null || valor.isBlank()) return new LinkedHashMap<>();
        return JSON.readValue(valor, TIPO);
    }
}
