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
