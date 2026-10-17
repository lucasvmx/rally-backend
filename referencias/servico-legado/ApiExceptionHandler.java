package br.org.fju.rally.servico;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> tratarRegra(IllegalArgumentException erro) {
        return ResponseEntity.badRequest().body(Map.of(
                "erro", "requisicao_invalida",
                "mensagem", erro.getMessage(),
                "data_hora", OffsetDateTime.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> tratarValidacao(MethodArgumentNotValidException erro) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "erro", "dados_invalidos",
                "mensagem", "Revise os campos informados.",
                "data_hora", OffsetDateTime.now()));
    }
}
