# rally-backend

Backend Spring Boot do Rally das Tribos, com persistência PostgreSQL e migrations Flyway.

## Desenvolvimento local

O projeto requer Java 25 e Maven 3.9. Configure um PostgreSQL acessível em `localhost:5432` ou ajuste as variáveis `RALLY_DATABASE_URL`, `RALLY_DATABASE_USUARIO` e `RALLY_DATABASE_SENHA`. Depois, execute:

```sh
mvn spring-boot:run
```

A API ficará disponível em `http://localhost:8080`. Para executar os testes automatizados, use `mvn test`.

O ambiente completo com PostgreSQL e frontend pode ser iniciado com `docker compose up --build` na pasta de trabalho `rally`, um nível acima deste repositório.

As orientações de execução, formulários públicos, Google Drive e plano tecnológico estão em [`documentacao/`](documentacao/). Os arquivos em [`referencias/servico-legado/`](referencias/servico-legado/) foram preservados para consulta; eles não fazem parte da compilação Maven.
