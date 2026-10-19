package br.org.fju.rally.servico;

import br.org.fju.rally.api.RespostaTribo;
import br.org.fju.rally.dominio.Tribo;
import br.org.fju.rally.repositorio.TriboRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class TriboService {
    private final TriboRepositorio tribos;
    private final AcessoService acesso;

    public TriboService(TriboRepositorio tribos, AcessoService acesso) {
        this.tribos = tribos;
        this.acesso = acesso;
    }

    @Transactional
    public RespostaTribo definirAtiva(UUID id, boolean ativa, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        Tribo tribo = tribos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tribo não encontrada."));
        tribo.definirAtiva(ativa);
        return RespostaTribo.de(tribos.save(tribo));
    }
}
