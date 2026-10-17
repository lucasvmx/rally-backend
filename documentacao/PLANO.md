# Plano tecnológico e visual do Rally das Tribos

## Resumo

Construir uma aplicação web responsiva, totalmente em português brasileiro, para automatizar o Rally das Tribos.

O placar principal será por tribo. As nações serão agrupadoras e participarão dos bônus nacionais.

O primeiro lançamento será um piloto com um rally e uma operação.

## Identidade visual

A aplicação utilizará exclusivamente esta paleta:

| Uso | Cor |
|---|---|
| Fundo principal | `#FFFFFF` |
| Cabeçalho, menu e textos fortes | `#2A2F42` |
| Variação clara da cor principal | `#4B526B` |
| Ações principais e destaques | `#A72727` |
| Hover, estados ativos e destaque intenso | `#651A1B` |
| Apoios, badges e superfícies secundárias | `#BE8282` |

Aplicação das cores:

- menu lateral e cabeçalho: `#2A2F42`;
- botões principais: `#A72727`;
- hover e estado selecionado: `#651A1B`;
- cartões e áreas de apoio: `#BE8282` com baixa opacidade;
- fundo das páginas: `#FFFFFF`;
- textos sobre fundos escuros: `#FFFFFF`;
- textos sobre `#BE8282`: `#2A2F42`;
- ranking da tribo em destaque: `#A72727`;
- ranking da nação como agrupamento: `#4B526B`.

A paleta será implementada como tokens visuais compartilhados no Angular Material e em variáveis CSS. `#BE8282` não será usado como texto pequeno sobre fundo branco por causa de contraste.

## Stack definida

### Frontend

- Angular 22.x;
- TypeScript;
- componentes standalone;
- Angular Material;
- Angular Signals;
- PWA com Service Worker;
- Vitest e Angular TestBed;
- Playwright para testes completos.

Angular recomenda componentes standalone em novos projetos e possui suporte oficial a Service Workers para aplicações PWA. ([componentes standalone](https://angular.dev/guide/components/importing), [Service Worker](https://angular.dev/ecosystem/service-workers))

### Backend

- Java 25;
- Spring Boot 4.1.x;
- GraalVM 25;
- Maven 3.9.x;
- Spring Web MVC;
- Spring Security;
- Spring Data JPA;
- Hibernate;
- Flyway;
- Jackson;
- `RestClient` do Spring.

Spring Boot 4.1 possui suporte a Java 25 e à compilação como Native Image com GraalVM 25. ([requisitos do Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html), [Native Image](https://docs.spring.io/spring-boot/how-to/native-image/developing-your-first-application.html))

### Banco e infraestrutura

- PostgreSQL 16 ou superior;
- UUIDs;
- Docker/OCI;
- backend compilado como Native Image em produção;
- perfil JVM para desenvolvimento;
- HTTPS obrigatório;
- segredos fora do código;
- infraestrutura compatível com qualquer provedor de containers e PostgreSQL.

## Organização do projeto

```text
/rally-web-app       # repositório Git do frontend
/rally-backend       # repositório Git do backend e documentação técnica
/docker-compose.yml  # orquestração local da pasta de trabalho rally
```

Todos os nomes próprios do domínio serão em português:

```text
PainelDaAssistente
PainelDoCoordenador
InterpretadorDeRegras
AnalisadorDeFotos
CalculadorDePontuacao
GerenciadorDeRelatorios
```

## Regras de idioma

Todo o sistema será em português brasileiro:

- telas;
- mensagens;
- notificações;
- relatórios;
- documentação;
- logs funcionais;
- nomes de campos;
- banco de dados;
- API interna;
- prompts da IA;
- respostas da IA;
- regras em JSON.

A única exceção será o contrato técnico externo exigido pelo OpenRouter e pelo Google Drive.

## OpenRouter

O OpenRouter será usado para:

- interpretar os recados semanais;
- gerar regras estruturadas;
- analisar fotos;
- sugerir contagens;
- apontar duplicidades ou evidências insuficientes.

Componentes internos:

```text
InterpretadorDeRegras
AnalisadorDeFotos
ValidadorDeRegras
```

As regras retornarão JSON com chaves em português, como:

```json
{
  "nome_missao": "Algo a mais",
  "data_inicio": "14/09/2026",
  "data_fim": "14/09/2026",
  "prazo_envio": "14/09/2026 23:00",
  "pontos_por_pessoa": 1500,
  "tipo_de_pessoa": "integrante",
  "exige_foto": true,
  "bonificacoes": []
}
```

A integração deverá:

- manter a chave somente no servidor;
- validar o JSON por schema;
- rejeitar respostas inválidas;
- exigir confirmação da assistente;
- permitir modelo principal e modelos reserva;
- limitar custos;
- registrar modelo, provedor e consumo;
- não registrar fotos nos logs;
- nunca aprovar pontuação automaticamente.

([documentação do OpenRouter](https://openrouter.ai/developers))

## Fluxo do rally

1. A assistente cola o recado.
2. O OpenRouter gera as regras em português.
3. A assistente revisa e publica.
4. O coordenador acessa apenas sua tribo.
5. Envia quantidade, fotos e identificações necessárias.
6. A IA sugere contagens.
7. O coordenador confirma.
8. A assistente revisa e aprova.
9. O sistema lança os pontos no placar da tribo.

Envios atrasados serão aceitos e marcados como atrasados.

## Pontuação e permissões

- cada missão conta separadamente;
- a mesma pessoa pode pontuar em missões diferentes;
- o bônus de cinco novos/afastados é calculado por tribo;
- o bônus “Alvo das Nações” beneficia as tribos da nação que alcançar a meta;
- regras publicadas ficam congeladas;
- bônus não podem ser lançados duas vezes;
- não haverá reconhecimento facial;
- novos/afastados serão identificados por nome, código ou marcação manual.

Assistentes veem e administram tudo. Coordenadores veem, enviam e corrigem apenas os relatórios da própria tribo.

## Google Drive

Uma assistente conecta sua conta Google e será proprietária inicial da pasta central.

```text
Rally
└── Semana
    └── Missão
        └── Nação
            └── Tribo
```

O sistema guardará apenas metadados e identificadores dos arquivos. As fotos serão baixadas temporariamente para análise e não serão tornadas públicas.

## Processamento e testes

No piloto, tarefas assíncronas usarão uma tabela de tarefas no PostgreSQL, sem RabbitMQ ou Kafka.

Testes:

- JUnit 5;
- Spring Boot Test;
- Testcontainers com PostgreSQL;
- testes de autorização;
- testes do cálculo de pontuação;
- testes de validação do JSON;
- testes de integração com Google Drive e OpenRouter;
- Vitest;
- Angular TestBed;
- Playwright;
- compilação e execução do Native Image.

## Critérios de aceitação

- interface integralmente em português;
- identidade visual aplicada com a paleta definida;
- ranking principal por tribo;
- regras em JSON com chaves em português;
- OpenRouter integrado com validação e fallback;
- fotos armazenadas no Google Drive;
- coordenadores isolados por tribo;
- assistentes com visão completa;
- pontuação auditável;
- bônus sem duplicidade;
- regras históricas preservadas;
- backend executando como Native Image.
