package br.org.fju.rally.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.util.UUID;

@Entity
@Table(name = "nacoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Nacao {
    @Id
    private UUID id;
    @Column(name = "nome", nullable = false)
    private String nome;

}
