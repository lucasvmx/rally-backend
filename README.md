# rally-backend

Backend Spring Boot do Rally das Tribos, com persistência PostgreSQL e migrations Flyway.

## Desenvolvimento local

O projeto requer Java 25 e Maven 3.9. Configure um PostgreSQL acessível em `localhost:5432` ou ajuste as variáveis `RALLY_DATABASE_URL`, `RALLY_DATABASE_USUARIO` e `RALLY_DATABASE_SENHA`. Depois, execute:

```sh
mvn spring-boot:run
```

A API ficará disponível em `http://localhost:8080`. Para executar os testes automatizados, use `mvn test`.

O ambiente completo com PostgreSQL e frontend pode ser iniciado com `docker compose up --build` na pasta de trabalho `rally`, um nível acima deste repositório.

## Implantação do backend

Para subir somente o backend e o PostgreSQL, configure `../.env` a partir de `../.env.exemplo`, preenchendo `POSTGRES_PASSWORD` e as credenciais OAuth do Google. Com GNU Make 4.x e Docker Compose instalados, execute a partir da pasta `rally-backend`:

```sh
make deploy
```

O serviço fica disponível na porta definida por `RALLY_BACKEND_PORT` (8080 por padrão), e os dados do banco persistem no volume `dados-postgres`. Use `make logs`, `make status` e `make down` para acompanhar e controlar os serviços. O comando `make down` preserva o volume do PostgreSQL.

As orientações de execução, formulários públicos, Google Drive e plano tecnológico estão em [`documentacao/`](documentacao/). Os arquivos em [`referencias/servico-legado/`](referencias/servico-legado/) foram preservados para consulta; eles não fazem parte da compilação Maven.

## CI/CD na VPS

O workflow [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) configura Temurin 25, executa `mvn verify` e implanta a branch `main` com `make deploy` em um runner Linux self-hosted. O runner precisa ter Maven 3.9 ou posterior, GNU Make 4.x e Docker Compose; para baixar o JDK, também precisa acessar os servidores de distribuição do Java. Também é possível iniciar a implantação manualmente pela aba **Actions** do GitHub, selecionando a branch `main`.

Configure os secrets do repositório `POSTGRES_PASSWORD`, `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` e `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET`. `OPENROUTER_API_KEY` também pode ser cadastrado como secret. As configurações não secretas podem ser cadastradas como variables, incluindo `POSTGRES_DB`, `POSTGRES_USER`, `RALLY_BACKEND_PORT`, `RALLY_FRONTEND_ORIGIN`, `RALLY_ASSISTENTES`, `RALLY_BACKEND_IMAGE`, `OPENROUTER_MODELO_REGRAS`, `OPENROUTER_MODELO_FOTOS`, `OPENROUTER_MODELOS_RESERVA` e `GOOGLE_DRIVE_PASTA_CENTRAL`. Se `RALLY_BACKEND_PORT` não for definido, o workflow usa `127.0.0.1:8082`, conforme o proxy Nginx de produção.

O Makefile continua aceitando `../.env` para uso local; quando esse arquivo não existe, o Compose recebe as variáveis exportadas pelo workflow do GitHub.
