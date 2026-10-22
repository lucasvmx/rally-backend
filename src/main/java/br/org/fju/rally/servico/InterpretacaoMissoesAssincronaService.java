package br.org.fju.rally.servico;

import br.org.fju.rally.api.RespostaInterpretacaoAssincrona;
import br.org.fju.rally.api.RespostaInterpretacaoAssincrona.Estado;
import br.org.fju.rally.api.RespostaInterpretacaoMissoes;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InterpretacaoMissoesAssincronaService {
    private static final int LIMITE_TAREFAS_REGISTRADAS = 300;
    private static final int LIMITE_TAREFAS_ATIVAS = 15;
    private static final int LIMITE_TAREFAS_ATIVAS_POR_USUARIO = 2;
    private static final int LIMITE_CANCELAMENTOS_ANTECIPADOS_POR_USUARIO = 10;
    private static final long RETENCAO_TERMINAL_MINUTOS = 5;

    private final InterpretadorDeRegras interpretador;
    private final ConcurrentHashMap<UUID, Tarefa> tarefas = new ConcurrentHashMap<>();
    private final Object travaAdmissao = new Object();
    private final ThreadPoolExecutor executor;

    public InterpretacaoMissoesAssincronaService(InterpretadorDeRegras interpretador) {
        this.interpretador = interpretador;
        AtomicInteger sequencia = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                3,
                3,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(12),
                trabalho -> {
                    Thread thread = new Thread(trabalho, "interpretacao-missoes-" + sequencia.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public RespostaInterpretacaoAssincrona iniciar(UUID id, String texto, UUID usuarioId) {
        synchronized (travaAdmissao) {
            limparTarefasExpiradas();
            Tarefa existente = tarefas.get(id);
            if (existente != null) {
                validarProprietario(existente, usuarioId);
                return existente.resposta();
            }
            validarCapacidade(usuarioId);

            Tarefa nova = new Tarefa(id, usuarioId, false);
            FutureTask<Void> trabalho = new FutureTask<>(() -> {
                processar(nova, texto);
                return null;
            });
            nova.registrarTrabalho(trabalho);
            tarefas.put(id, nova);
            try {
                executor.execute(trabalho);
            } catch (RejectedExecutionException excecao) {
                nova.falhar("O serviço de interpretação está ocupado. Aguarde um pouco e tente novamente.");
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "O serviço de interpretação está ocupado. Aguarde um pouco e tente novamente.");
            }
            return nova.resposta();
        }
    }

    public RespostaInterpretacaoAssincrona consultar(UUID id, UUID usuarioId) {
        limparTarefasExpiradas();
        return obterTarefa(id, usuarioId).resposta();
    }

    public void cancelar(UUID id, UUID usuarioId) {
        synchronized (travaAdmissao) {
            limparTarefasExpiradas();
            Tarefa tarefa = tarefas.get(id);
            if (tarefa == null) {
                if (tarefas.size() >= LIMITE_TAREFAS_REGISTRADAS
                        || contarCancelamentosAntecipados(usuarioId) >= LIMITE_CANCELAMENTOS_ANTECIPADOS_POR_USUARIO) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Não foi possível registrar o cancelamento. Tente novamente.");
                }
                tarefa = new Tarefa(id, usuarioId, true);
                tarefas.put(id, tarefa);
            } else {
                validarProprietario(tarefa, usuarioId);
            }
            tarefa.cancelar();
            FutureTask<Void> trabalho = tarefa.trabalho();
            if (trabalho != null) executor.remove(trabalho);
        }
    }

    @PreDestroy
    public void encerrar() {
        tarefas.values().forEach(Tarefa::cancelar);
        executor.shutdownNow();
    }

    private void processar(Tarefa tarefa, String texto) {
        if (!tarefa.iniciar()) return;
        try {
            RespostaInterpretacaoMissoes resultado = interpretador.interpretarMissoes(texto);
            tarefa.concluir(resultado);
        } catch (RuntimeException excecao) {
            tarefa.falhar(mensagemSegura(excecao));
        }
    }

    private String mensagemSegura(RuntimeException excecao) {
        if (excecao instanceof ResponseStatusException resposta && resposta.getReason() != null) {
            return resposta.getReason();
        }
        return "Não foi possível interpretar as missões. Tente novamente.";
    }

    private Tarefa obterTarefa(UUID id, UUID usuarioId) {
        Tarefa tarefa = tarefas.get(id);
        if (tarefa == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interpretação não encontrada.");
        }
        validarProprietario(tarefa, usuarioId);
        return tarefa;
    }

    private void validarProprietario(Tarefa tarefa, UUID usuarioId) {
        if (!tarefa.usuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interpretação não encontrada.");
        }
    }

    private void validarCapacidade(UUID usuarioId) {
        if (tarefas.size() >= LIMITE_TAREFAS_REGISTRADAS
                || executor.getActiveCount() + executor.getQueue().size() >= LIMITE_TAREFAS_ATIVAS
                || tarefas.values().stream().filter(Tarefa::ativa)
                .filter(tarefa -> tarefa.usuarioId().equals(usuarioId)).count() >= LIMITE_TAREFAS_ATIVAS_POR_USUARIO) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O serviço de interpretação está ocupado. Aguarde um pouco e tente novamente.");
        }
    }

    private void limparTarefasExpiradas() {
        synchronized (travaAdmissao) {
            Instant limite = Instant.now().minus(RETENCAO_TERMINAL_MINUTOS, ChronoUnit.MINUTES);
            tarefas.entrySet().removeIf(entrada -> entrada.getValue().expirada(limite));
        }
    }

    private long contarCancelamentosAntecipados(UUID usuarioId) {
        return tarefas.values().stream()
                .filter(Tarefa::cancelamentoAntecipado)
                .filter(tarefa -> tarefa.usuarioId().equals(usuarioId))
                .count();
    }

    private static final class Tarefa {
        private final UUID id;
        private final UUID usuarioId;
        private final boolean cancelamentoAntecipado;
        private Estado estado = Estado.NA_FILA;
        private RespostaInterpretacaoMissoes resultado;
        private String mensagemErro;
        private Instant atualizadaEm = Instant.now();
        private FutureTask<Void> trabalho;

        private Tarefa(UUID id, UUID usuarioId, boolean cancelamentoAntecipado) {
            this.id = id;
            this.usuarioId = usuarioId;
            this.cancelamentoAntecipado = cancelamentoAntecipado;
        }

        private UUID usuarioId() {
            return usuarioId;
        }

        private boolean cancelamentoAntecipado() {
            return cancelamentoAntecipado;
        }

        private synchronized boolean ativa() {
            return estado == Estado.NA_FILA || estado == Estado.PROCESSANDO;
        }

        private synchronized void registrarTrabalho(FutureTask<Void> trabalho) {
            this.trabalho = trabalho;
            if (estado == Estado.CANCELADA) trabalho.cancel(true);
        }

        private synchronized FutureTask<Void> trabalho() {
            return trabalho;
        }

        private synchronized boolean iniciar() {
            if (estado != Estado.NA_FILA) return false;
            estado = Estado.PROCESSANDO;
            atualizadaEm = Instant.now();
            return true;
        }

        private synchronized void concluir(RespostaInterpretacaoMissoes resultado) {
            if (estado != Estado.NA_FILA && estado != Estado.PROCESSANDO) return;
            this.resultado = resultado;
            this.mensagemErro = null;
            this.estado = Estado.CONCLUIDA;
            this.atualizadaEm = Instant.now();
        }

        private synchronized void falhar(String mensagem) {
            if (estado != Estado.NA_FILA && estado != Estado.PROCESSANDO) return;
            this.resultado = null;
            this.mensagemErro = mensagem;
            this.estado = Estado.FALHOU;
            this.atualizadaEm = Instant.now();
        }

        private synchronized void cancelar() {
            if (estado == Estado.CONCLUIDA || estado == Estado.FALHOU || estado == Estado.CANCELADA) return;
            estado = Estado.CANCELADA;
            resultado = null;
            mensagemErro = null;
            atualizadaEm = Instant.now();
            if (trabalho != null) trabalho.cancel(true);
        }

        private synchronized boolean expirada(Instant limite) {
            return (estado == Estado.CONCLUIDA || estado == Estado.FALHOU || estado == Estado.CANCELADA)
                    && atualizadaEm.isBefore(limite);
        }

        private synchronized RespostaInterpretacaoAssincrona resposta() {
            return new RespostaInterpretacaoAssincrona(id, estado, resultado, mensagemErro);
        }
    }
}
