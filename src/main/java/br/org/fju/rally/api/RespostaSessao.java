package br.org.fju.rally.api;

public record RespostaSessao(boolean autenticado, String email, String nome, String provedor, boolean emailVerificado) {}
