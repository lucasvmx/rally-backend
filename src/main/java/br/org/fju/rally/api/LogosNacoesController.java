package br.org.fju.rally.api;

import br.org.fju.rally.servico.LogoNacaoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nacoes")
public class LogosNacoesController {
    private final LogoNacaoService logos;

    public LogosNacoesController(LogoNacaoService logos) {
        this.logos = logos;
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable UUID id,
                                       @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return logos.obter(id, ifNoneMatch);
    }

    @PutMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> salvar(@PathVariable UUID id,
                                       @RequestPart("arquivo") MultipartFile arquivo,
                                       HttpServletRequest requisicao) {
        logos.salvar(id, arquivo, requisicao);
        return ResponseEntity.noContent().build();
    }
}
