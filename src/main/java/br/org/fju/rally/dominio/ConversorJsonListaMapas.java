package br.org.fju.rally.dominio;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Converter
public class ConversorJsonListaMapas implements AttributeConverter<List<Map<String, Object>>, String> {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Map<String, Object>>> TIPO = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<Map<String, Object>> atributo) {
        return atributo == null ? "[]" : JSON.writeValueAsString(atributo);
    }

    @Override
    public List<Map<String, Object>> convertToEntityAttribute(String valor) {
        if (valor == null || valor.isBlank()) return new ArrayList<>();
        return JSON.readValue(valor, TIPO);
    }
}
