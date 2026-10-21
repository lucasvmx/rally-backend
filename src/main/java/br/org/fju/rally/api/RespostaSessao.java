package br.org.fju.rally.api;

import br.org.fju.rally.servico.PapelUsuario;

import java.util.UUID;

public record RespostaSessao(boolean autenticado, String email, String nome, String provedor,
                             boolean emailVerificado, PapelUsuario role, UUID tribeId,
                             UUID nationId, String accessToken, boolean trocaSenhaObrigatoria) {}
