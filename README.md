# rally-backend

Backend Spring Boot do Rally das Tribos, com persistência PostgreSQL e migrations Flyway.

## Desenvolvimento local

O projeto requer Java 25 e Maven 3.9. O contrato gRPC de detecção facial é baixado do GitHub Packages: configure `RALLY_FACE_DETECTION_PACKAGE_OWNER` e um `~/.m2/settings.xml` com credencial de leitura para o server id `github`. Para builds Docker, o Compose passa o owner como argumento público e o Maven settings, `GITHUB_ACTOR` e `GITHUB_TOKEN` como secrets temporários do BuildKit. Se o settings usar `${env.GITHUB_ACTOR}` e `${env.GITHUB_TOKEN}`, exporte essas variáveis ou preencha-as no `.env` local; o token precisa de `read:packages`. Configure um PostgreSQL acessível em `localhost:5432` ou ajuste as variáveis `RALLY_DATABASE_URL`, `RALLY_DATABASE_USUARIO` e `RALLY_DATABASE_SENHA`. Depois, execute:

```sh
mvn spring-boot:run
```

A API ficará disponível em `http://localhost:8080`. Para executar os testes automatizados, use `mvn test`.

O ambiente completo com PostgreSQL e frontend pode ser iniciado com `docker compose up --build` na pasta de trabalho `rally`, um nível acima deste repositório.

## Implantação do backend

Para subir somente o backend e o PostgreSQL, configure `../.env` a partir de `../.env.exemplo`, preenchendo `POSTGRES_PASSWORD`, `RALLY_JWT_SECRET`, `RALLY_FACE_DETECTION_TOKEN` e as credenciais OAuth do Google. `RALLY_FACE_DETECTION_TOKEN` deve ser o mesmo segredo aleatório de ao menos 32 caracteres configurado no serviço gRPC. Gere a chave JWT com `openssl rand -base64 32`; use o mesmo valor em todas as instâncias de produção. Configure ao menos um e-mail em `RALLY_ADMINISTRADORES_SISTEMA` para criar o primeiro administrador do sistema no primeiro login Google verificado. Configure também `RALLY_FACE_DETECTION_PACKAGE_OWNER` e `RALLY_MAVEN_SETTINGS_FILE` para que o build Docker leia o artefato privado sem gravar credenciais na imagem. Com GNU Make 4.x e Docker Compose instalados, execute a partir da pasta `rally-backend`:

```sh
make deploy
```

O serviço fica disponível na porta definida por `RALLY_BACKEND_PORT` (8080 por padrão), e os dados do banco persistem no volume `dados-postgres`. Use `make logs`, `make status` e `make down` para acompanhar e controlar os serviços. O comando `make down` preserva o volume do PostgreSQL.

As orientações de execução, formulários públicos, Google Drive e plano tecnológico estão em [`documentacao/`](documentacao/). Os arquivos em [`referencias/servico-legado/`](referencias/servico-legado/) foram preservados para consulta; eles não fazem parte da compilação Maven.

## Logs de requisições

Em nível INFO, o backend registra início e término das requisições HTTP recebidas (`http_recebido_inicio` / `http_recebido_fim`) e enviadas pelo `RestClient` (`http_saida_inicio` / `http_saida_fim`), incluindo OpenRouter e Google Drive. O campo `request_id` relaciona a entrada às chamadas externas; `chamada_id` diferencia cada tentativa externa, inclusive chamadas a modelos de reserva.

O término contém `status` e `duracao_ms`, calculada com relógio monotônico. Na saída, a duração vai até o fechamento da resposta pelo cliente, incluindo sua leitura; falhas de transporte geram `http_saida_falha` com a classe do erro e o tempo decorrido. As entradas atuais são síncronas e medem o processamento até o retorno da cadeia de filtros.

Exemplo ilustrativo de resposta do OpenRouter:

```text
http_saida_fim request_id=... chamada_id=... metodo=POST destino=openrouter.ai status=200 duracao_ms=1842
```

Para proteger credenciais e dados pessoais, os logs omitem corpos, cabeçalhos e parâmetros de URL. Nas entradas, usam o template da rota (por exemplo, `/api/v1/publico/formularios/{token}`); requisições encerradas antes do mapeamento, como rejeições da segurança, usam `[nao_mapeada]`. Nas saídas, registram somente o host do destino. Consulte os registros com `make logs`.

## CI/CD na VPS

O workflow [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) configura Temurin 25, lê o pacote Maven com `GITHUB_TOKEN`, executa `mvn verify` e implanta a branch `main` com `make deploy` em um runner Linux self-hosted. O runner precisa ter Maven 3.9 ou posterior, GNU Make 4.x, Docker Compose com BuildKit e acesso de leitura ao pacote GitHub `rally-face-detection-proto`; para baixar o JDK, também precisa acessar os servidores de distribuição do Java. O serviço gRPC deve estar ativo na VPS e sua porta deve aceitar tráfego apenas da rede Docker. Também é possível iniciar a implantação manualmente pela aba **Actions** do GitHub, selecionando a branch `main`.

Configure os secrets do repositório `POSTGRES_PASSWORD`, `RALLY_JWT_SECRET`, `RALLY_FACE_DETECTION_TOKEN`, `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` e `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET`. `OPENROUTER_API_KEY` também pode ser cadastrado como secret. As configurações não secretas podem ser cadastradas como variables, incluindo `RALLY_FACE_DETECTION_PACKAGE_OWNER`, `RALLY_FACE_DETECTION_PORT`, `RALLY_FACE_DETECTION_TIMEOUT_MS`, `POSTGRES_DB`, `POSTGRES_USER`, `RALLY_BACKEND_PORT`, `RALLY_FRONTEND_ORIGIN`, `RALLY_ASSISTENTES`, `RALLY_ADMINISTRADORES_SISTEMA`, `RALLY_BACKEND_IMAGE`, `OPENROUTER_MODELO_REGRAS`, `OPENROUTER_MODELO_FOTOS`, `OPENROUTER_MODELOS_RESERVA` e `GOOGLE_DRIVE_PASTA_CENTRAL`. Se `RALLY_BACKEND_PORT` não for definido, o workflow usa `127.0.0.1:8082`, conforme o proxy Nginx de produção.

Sem overrides, a integração usa `deepseek/deepseek-v4.1-flash-1017` para texto e para o campo de modelo de fotos, com `qwen/qwen3-next-80b-a3b-thinking` como reserva. O backend ainda não envia imagens à IA; o campo `OPENROUTER_MODELO_FOTOS` fica configurado para esse uso futuro.

O Makefile continua aceitando `../.env` para uso local; quando esse arquivo não existe, o Compose recebe as variáveis exportadas pelo workflow do GitHub.
