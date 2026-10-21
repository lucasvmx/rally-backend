package br.org.fju.rally.servico;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class GeradorSenhaProvisoria {
    private static final char[] CONSOANTES = "bdfghjklmnprstvz".toCharArray();
    private static final char[] VOGAIS = "aeiou".toCharArray();
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public String gerar() {
        return IntStream.range(0, 4).mapToObj(ignorado -> palavra())
                .collect(Collectors.joining("-"));
    }

    private String palavra() {
        StringBuilder palavra = new StringBuilder(6);
        for (int silaba = 0; silaba < 3; silaba++) {
            palavra.append(CONSOANTES[ALEATORIO.nextInt(CONSOANTES.length)]);
            palavra.append(VOGAIS[ALEATORIO.nextInt(VOGAIS.length)]);
        }
        return palavra.toString();
    }
}
