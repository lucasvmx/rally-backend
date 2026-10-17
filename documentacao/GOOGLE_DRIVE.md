# Conexão com o Google Drive

## Configuração do Google Cloud

1. Crie um projeto no Google Cloud.
2. Ative a Google Drive API.
3. Crie uma credencial OAuth 2.0 para aplicação web.
4. Cadastre o endereço de retorno:
   `https://SEU_DOMINIO/login/oauth2/code/google`
5. Use o escopo `https://www.googleapis.com/auth/drive.file`.

## Variáveis do backend

Em produção, ative o perfil `producao` e forneça os valores por segredo do ambiente:

```text
SPRING_PROFILES_ACTIVE=producao
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=openid,profile,email,https://www.googleapis.com/auth/drive.file
GOOGLE_DRIVE_PASTA_CENTRAL=
```

`GOOGLE_DRIVE_PASTA_CENTRAL` pode receber o identificador de uma pasta central existente. Se ficar vazio, o sistema cria a pasta `Rally` na raiz do Drive da assistente.

As fotos seguem a estrutura `Rally/Semana/Missão/Nação/Tribo`. O banco guarda somente o identificador do arquivo, nome, tipo, tamanho, hash e data do envio. O sistema não publica os arquivos nem grava seu conteúdo nos logs.
