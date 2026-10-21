package br.org.fju.rally.servico;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class LogoNacaoService {
    private static final long LIMITE_ARQUIVO = 5L * 1024 * 1024;
    private static final long LIMITE_PIXELS_ORIGINAIS = 60_000_000L;
    private static final int LIMITE_LADO_ORIGINAL = 20_000;
    private static final int LADO_LOGO = 512;

    private final JdbcTemplate banco;
    private final AcessoService acesso;

    public LogoNacaoService(JdbcTemplate banco, AcessoService acesso) {
        this.banco = banco;
        this.acesso = acesso;
    }

    @Transactional
    public void salvar(UUID nacaoId, MultipartFile arquivo, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        Boolean nacaoExiste = banco.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM nacoes WHERE id = ?)", Boolean.class, nacaoId);
        if (!Boolean.TRUE.equals(nacaoExiste)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nação não encontrada.");
        }
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma imagem PNG ou JPEG.");
        }
        if (arquivo.getSize() > LIMITE_ARQUIVO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A imagem deve ter no máximo 5 MB.");
        }

        final byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException excecao) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler a imagem enviada.");
        }
        ImagemNormalizada imagem = normalizar(conteudo);
        banco.update("""
                INSERT INTO logos_nacoes (nacao_id, tipo_arquivo, arquivo, atualizada_em)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (nacao_id) DO UPDATE SET
                    tipo_arquivo = EXCLUDED.tipo_arquivo,
                    arquivo = EXCLUDED.arquivo,
                    atualizada_em = CURRENT_TIMESTAMP
                """, nacaoId, imagem.tipoConteudo(), imagem.conteudo());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> obter(UUID nacaoId, String ifNoneMatch) {
        LogoNacao logo = banco.query("""
                SELECT tipo_arquivo, arquivo, atualizada_em
                FROM logos_nacoes
                WHERE nacao_id = ?
                """, (resultado, indice) -> new LogoNacao(
                resultado.getString("tipo_arquivo"),
                resultado.getBytes("arquivo"),
                resultado.getTimestamp("atualizada_em").toInstant()), nacaoId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Logo não encontrada."));

        String etag = "\"" + hash(logo.conteudo()) + "\"";
        ResponseEntity.BodyBuilder resposta = ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag(etag)
                .lastModified(logo.atualizadaEm().toEpochMilli())
                .header("X-Content-Type-Options", "nosniff");
        if (correspondeAoEtag(ifNoneMatch, etag)) {
            return ResponseEntity.<byte[]>status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .eTag(etag)
                    .lastModified(logo.atualizadaEm().toEpochMilli())
                    .header("X-Content-Type-Options", "nosniff")
                    .build();
        }
        return resposta.contentType(MediaType.parseMediaType(logo.tipoConteudo()))
                .contentLength(logo.conteudo().length)
                .body(logo.conteudo());
    }

    private ImagemNormalizada normalizar(byte[] bytes) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (entrada == null) throw imagemInvalida();
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) throw imagemInvalida();

            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(entrada, true, true);
                String formato = leitor.getFormatName().toLowerCase(Locale.ROOT);
                String tipoConteudo = switch (formato) {
                    case "png" -> "image/png";
                    case "jpeg", "jpg" -> "image/jpeg";
                    default -> throw imagemInvalida();
                };
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                long pixels = (long) largura * altura;
                if (largura <= 0 || altura <= 0 || largura > LIMITE_LADO_ORIGINAL
                        || altura > LIMITE_LADO_ORIGINAL || pixels > LIMITE_PIXELS_ORIGINAIS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A resolução da imagem enviada é muito alta.");
                }

                int amostragem = Math.max(1, (int) Math.ceil(Math.sqrt(
                        (double) pixels / (LADO_LOGO * LADO_LOGO))));
                ImageReadParam parametros = leitor.getDefaultReadParam();
                parametros.setSourceSubsampling(amostragem, amostragem, 0, 0);
                BufferedImage origem = leitor.read(0, parametros);
                if (origem == null) throw imagemInvalida();

                boolean png = "image/png".equals(tipoConteudo);
                BufferedImage quadrada = new BufferedImage(LADO_LOGO, LADO_LOGO,
                        png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                Graphics2D grafico = quadrada.createGraphics();
                try {
                    grafico.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    grafico.setRenderingHint(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);
                    grafico.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    if (!png) {
                        grafico.setColor(Color.WHITE);
                        grafico.fillRect(0, 0, LADO_LOGO, LADO_LOGO);
                    }
                    double escala = Math.min((double) LADO_LOGO / origem.getWidth(),
                            (double) LADO_LOGO / origem.getHeight());
                    int larguraFinal = Math.max(1, (int) Math.round(origem.getWidth() * escala));
                    int alturaFinal = Math.max(1, (int) Math.round(origem.getHeight() * escala));
                    int x = (LADO_LOGO - larguraFinal) / 2;
                    int y = (LADO_LOGO - alturaFinal) / 2;
                    grafico.drawImage(origem, x, y, larguraFinal, alturaFinal, null);
                } finally {
                    grafico.dispose();
                    origem.flush();
                }

                try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
                    String formatoSaida = png ? "png" : "jpeg";
                    if (!ImageIO.write(quadrada, formatoSaida, saida)) throw imagemInvalida();
                    quadrada.flush();
                    return new ImagemNormalizada(tipoConteudo, saida.toByteArray());
                }
            } finally {
                leitor.dispose();
            }
        } catch (ResponseStatusException excecao) {
            throw excecao;
        } catch (IOException | RuntimeException excecao) {
            throw imagemInvalida();
        }
    }

    private ResponseStatusException imagemInvalida() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "O arquivo deve ser uma imagem PNG ou JPEG válida.");
    }

    private String hash(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível.", excecao);
        }
    }

    private boolean correspondeAoEtag(String recebido, String etag) {
        if (recebido == null || recebido.isBlank()) return false;
        return java.util.Arrays.stream(recebido.split(","))
                .map(String::trim)
                .anyMatch(valor -> valor.equals("*") || valor.equals(etag) || valor.equals("W/" + etag));
    }

    private record ImagemNormalizada(String tipoConteudo, byte[] conteudo) {}
    private record LogoNacao(String tipoConteudo, byte[] conteudo, Instant atualizadaEm) {}
}
