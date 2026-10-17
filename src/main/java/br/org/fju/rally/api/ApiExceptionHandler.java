package br.org.fju.rally.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> tratarRespostaStatus(ResponseStatusException excecao) {
        return ResponseEntity.status(excecao.getStatusCode())
                .body(Map.of("mensagem", excecao.getReason() == null ? "Não foi possível concluir a operação." : excecao.getReason()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> tratarArgumento(IllegalArgumentException excecao) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensagem", excecao.getMessage() == null ? "Dados inválidos." : excecao.getMessage()));
    }
}
