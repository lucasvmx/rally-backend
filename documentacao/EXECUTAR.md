# Executar o Rally das Tribos

## Ambiente completo

1. Na pasta de trabalho `rally`, copie `.env.exemplo` para `.env` e preencha os segredos necessários.
2. Execute `docker compose up --build` nessa pasta. O Compose usa os repositórios `rally-web-app` e `rally-backend` como contextos.
3. Abra `http://localhost:4200`.
4. A API ficará disponível em `http://localhost:8080/api/v1/saude`.

As migrations do Flyway são aplicadas automaticamente quando o backend inicia. O Hibernate está configurado com `ddl-auto: validate`, portanto não cria nem altera tabelas.

## Desenvolvimento separado

O frontend pode ser iniciado com `npm ci` e `npm start` dentro de `rally-web-app`.

Para validar o backend com o Java disponível no ambiente local, use Java 17 como compatibilidade de compilação:

```text
mvn -Dmaven.compiler.release=17 -Djava.version=17 test
```

Execute o comando dentro de `rally-backend`.

## Login do painel

Em produção, as contas locais são restritas aos e-mails listados em `RALLY_ASSISTENTES`. No primeiro acesso, a assistente deve escolher **Defina uma senha com o Google** na tela de entrada, autenticar-se com o Google e cadastrar uma senha local. Os próximos acessos podem usar e-mail e senha; uma senha correta não libera uma conta bloqueada.

Após três tentativas locais incorretas, a conta fica bloqueada. Para recuperar o acesso, use novamente o fluxo Google verificado para redefinir a senha; essa ação também remove o bloqueio. Não há cadastro público. Formulários de envio público continuam sem exigir login.

No modo de demonstração, o painel entra automaticamente e o fluxo de login local não é usado.

A imagem de produção usa Java 25. O perfil nativo usa GraalVM 25 e deve ser executado em ambiente com suporte ao compilador Native Image.

## Google Drive

Configure uma aplicação OAuth do Google com o escopo `https://www.googleapis.com/auth/drive.file`. A conta da assistente inicia a conexão e é a proprietária das pastas. O backend guarda apenas os metadados do arquivo e seu identificador no Drive.

As variáveis e o endereço de retorno estão detalhados em [GOOGLE_DRIVE.md](GOOGLE_DRIVE.md).
