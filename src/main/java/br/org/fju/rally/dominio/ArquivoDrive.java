package br.org.fju.rally.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "arquivos_drive")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ArquivoDrive {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relatorio_id", nullable = false)
    private Relatorio relatorio;

    @Column(name = "identificador_drive", nullable = false)
    private String identificadorDrive;
    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;
    @Column(name = "tipo_arquivo")
    private String tipoArquivo;
    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;
    @Column(name = "hash_arquivo")
    private String hashArquivo;
    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;
}
