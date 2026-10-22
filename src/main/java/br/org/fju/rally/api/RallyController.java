package br.org.fju.rally.api;

import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.NacaoRepositorio;
import br.org.fju.rally.repositorio.TriboRepositorio;
import br.org.fju.rally.repositorio.RallyRepositorio;
import br.org.fju.rally.servico.MissaoService;
import br.org.fju.rally.servico.GoogleDriveService;
import br.org.fju.rally.servico.PontuacaoService;
import br.org.fju.rally.servico.PontuacaoManualService;
import br.org.fju.rally.servico.RelatorioService;
import br.org.fju.rally.servico.TriboService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1")
public class RallyController {
    private final NacaoRepositorio nacaoRepositorio;
    private final TriboRepositorio triboRepositorio;
    private final MissaoRepositorio missaoRepositorio;
    private final RelatorioService relatorioService;
    private final PontuacaoService pontuacaoService;
    private final MissaoService missaoService;
    private final GoogleDriveService googleDriveService;
    private final RallyRepositorio rallyRepositorio;
    private final TriboService triboService;
    private final PontuacaoManualService pontuacaoManualService;

    public RallyController(NacaoRepositorio nacaoRepositorio, TriboRepositorio triboRepositorio,
                           MissaoRepositorio missaoRepositorio, RelatorioService relatorioService,
                           PontuacaoService pontuacaoService, MissaoService missaoService,
                           GoogleDriveService googleDriveService, RallyRepositorio rallyRepositorio,
                           TriboService triboService, PontuacaoManualService pontuacaoManualService) {
        this.nacaoRepositorio = nacaoRepositorio;
        this.triboRepositorio = triboRepositorio;
        this.missaoRepositorio = missaoRepositorio;
        this.relatorioService = relatorioService;
        this.pontuacaoService = pontuacaoService;
        this.missaoService = missaoService;
        this.googleDriveService = googleDriveService;
        this.rallyRepositorio = rallyRepositorio;
        this.triboService = triboService;
        this.pontuacaoManualService = pontuacaoManualService;
    }

    @GetMapping("/saude")
    public RespostaSaude saude() {
        return new RespostaSaude("ok", "servico-rally", "pt-BR");
    }

    @GetMapping("/nacoes")
    public List<RespostaNacao> nacoes() {
        return nacaoRepositorio.findAll().stream().map(RespostaNacao::de).toList();
    }

    @GetMapping("/tribos")
    public List<RespostaTribo> tribos() {
        return triboRepositorio.buscarTodasComNacao().stream().map(RespostaTribo::de).toList();
    }

    @PatchMapping("/tribos/{id}")
    public RespostaTribo definirSituacaoTribo(@PathVariable UUID id,
            @Valid @RequestBody RequisicaoStatusTribo corpo, HttpServletRequest requisicao) {
        return triboService.definirAtiva(id, corpo.ativa(), requisicao);
    }

    @GetMapping("/missoes")
    public List<RespostaMissao> missoes() {
        return missaoRepositorio.buscarTodasComRally().stream().map(RespostaMissao::de).toList();
    }

    @GetMapping("/rallies")
    public List<RespostaRally> rallies() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        return rallyRepositorio.findAll().stream()
                .sorted(java.util.Comparator.comparing(br.org.fju.rally.dominio.Rally::getDataInicio).reversed())
                .map(rally -> RespostaRally.de(rally, hoje))
                .toList();
    }

    @PostMapping("/rallies")
    @ResponseStatus(HttpStatus.CREATED)
    public RespostaRally criarRally(@Valid @RequestBody DadosRally corpo, HttpServletRequest requisicao) {
        return missaoService.criarRally(corpo, requisicao);
    }

    @DeleteMapping("/rallies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirRally(@PathVariable UUID id, HttpServletRequest requisicao) {
        missaoService.excluirRally(id, requisicao);
    }

    @PostMapping("/missoes/interpretar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RespostaInterpretacaoAssincrona iniciarInterpretacaoMissoes(
            @RequestHeader("X-Interpretacao-Id") UUID id,
            @Valid @RequestBody RequisicaoInterpretacao corpo, HttpServletRequest requisicao) {
        return missaoService.iniciarInterpretacao(id, corpo, requisicao);
    }

    @GetMapping("/missoes/interpretar/{id}")
    public RespostaInterpretacaoAssincrona consultarInterpretacaoMissoes(@PathVariable UUID id,
                                                                         HttpServletRequest requisicao) {
        return missaoService.consultarInterpretacao(id, requisicao);
    }

    @DeleteMapping("/missoes/interpretar/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarInterpretacaoMissoes(@PathVariable UUID id, HttpServletRequest requisicao) {
        missaoService.cancelarInterpretacao(id, requisicao);
    }

    @PostMapping("/missoes")
    public List<RespostaMissao> criarMissoes(@Valid @RequestBody RequisicaoCriarMissoes corpo,
                                              HttpServletRequest requisicao) {
        return missaoService.criar(corpo, requisicao);
    }

    @DeleteMapping("/missoes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirMissao(@PathVariable UUID id, HttpServletRequest requisicao) {
        missaoService.excluirMissao(id, requisicao);
    }

    @GetMapping("/relatorios")
    public List<RespostaRelatorio> relatorios(HttpServletRequest requisicao) {
        return relatorioService.listar(requisicao);
    }

    @GetMapping("/relatorios/{id}/dados-missao")
    public RespostaDadosMissaoRelatorio dadosMissaoRelatorio(@PathVariable UUID id,
                                                               HttpServletRequest requisicao) {
        return relatorioService.dadosMissao(id, requisicao);
    }

    @PostMapping("/relatorios")
    public RespostaRelatorio criarRelatorio(@Valid @RequestBody RequisicaoRelatorio corpo,
                                            HttpServletRequest requisicao) {
        return relatorioService.criar(corpo, requisicao);
    }

    @PostMapping("/relatorios/{id}/aprovar")
    public RespostaRelatorio aprovar(@PathVariable UUID id, @RequestBody(required = false) RequisicaoRevisao corpo,
                                     HttpServletRequest requisicao) {
        return relatorioService.aprovar(id, corpo == null ? null : corpo.justificativa(), requisicao);
    }

    @PostMapping("/relatorios/{id}/rejeitar")
    public RespostaRelatorio rejeitar(@PathVariable UUID id, @RequestBody(required = false) RequisicaoRevisao corpo,
                                      HttpServletRequest requisicao) {
        return relatorioService.rejeitar(id, corpo == null ? null : corpo.justificativa(), requisicao);
    }

    @PostMapping(value = "/relatorios/{id}/arquivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String enviarArquivo(@PathVariable UUID id, @RequestPart("arquivo") MultipartFile arquivo,
                                HttpServletRequest requisicao, Authentication autenticacao) {
        return googleDriveService.enviar(id, arquivo, requisicao, autenticacao).getIdentificadorDrive();
    }

    @GetMapping("/placar")
    public List<RespostaPlacar> placar(@RequestParam(name = "rally_id", required = false) UUID rallyId) {
        return rallyId == null ? pontuacaoService.gerarPlacar() : pontuacaoService.gerarPlacar(rallyId);
    }

    @GetMapping("/pontuacoes-manuais")
    public List<RespostaPontuacaoManual> pontuacoesManuais(HttpServletRequest requisicao) {
        return pontuacaoManualService.listar(requisicao);
    }

    @PostMapping("/pontuacoes-manuais")
    public RespostaPontuacaoManual registrarPontuacaoManual(
            @Valid @RequestBody RequisicaoPontuacaoManual corpo, HttpServletRequest requisicao) {
        return pontuacaoManualService.registrar(corpo, requisicao);
    }

    @PostMapping("/regras/interpretar")
    public RespostaInterpretacao interpretar(@Valid @RequestBody RequisicaoInterpretacao corpo,
                                              HttpServletRequest requisicao) {
        return missaoService.interpretarRegras(corpo, requisicao);
    }
}
