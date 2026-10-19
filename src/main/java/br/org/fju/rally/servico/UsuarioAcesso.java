package br.org.fju.rally.servico;

import java.util.UUID;

public record UsuarioAcesso(UUID id, String email, PapelUsuario papel, UUID triboId, UUID nacaoId,
                            String googleSubject) {}
