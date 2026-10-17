# Formulários de relatórios

Na aplicação, abra **Administração → Formulários**. Escolha uma missão, informe título e descrição e adicione as perguntas necessárias. Há texto curto, texto longo, número e lista de opções; cada pergunta pode ser obrigatória e sua ordem pode ser alterada.

Ao publicar, copie o link exibido e compartilhe com as pessoas responsáveis pelos envios. Cada publicação cria um formulário independente e imutável. Para substituir perguntas de um formulário publicado, publique outro e desative o link anterior.

## Link público

O endereço é `/enviar-relatorio/<segredo>`. O backend gera um segredo criptográfico de 256 bits (64 caracteres hexadecimais), independente do identificador do formulário. Nenhum segredo é fixado no código do frontend. Quem tiver o link pode enviar e encaminhá-lo para outras pessoas; o link não identifica individualmente o remetente.

O acesso não exige login e não expõe a lista de relatórios nem a revisão administrativa. Links inexistentes e desativados recebem a mesma resposta 404. A desativação impede novas consultas e novos envios, inclusive quando alguém já havia aberto o formulário.

Os dados do formulário e as respostas não são armazenados no cache offline. A tela pública informa aos mecanismos de busca que não deve ser indexada e desabilita o envio do endereço como referência.

## Recebimento e revisão

Todo formulário inclui tribo, quantidades de pessoas e novos ou afastados, observações e foto da evidência. Quando a missão exige foto, ela também é obrigatória no servidor. São aceitos JPEG, PNG e WebP de até 5 MB, com conferência da assinatura do arquivo.

O envio gera um relatório com status `ENVIADO`, usado pela fila de revisão existente, e devolve um protocolo ao remetente. Respostas personalizadas e foto ficam vinculadas ao relatório. Em **Relatórios → Ver envio**, a assistente pode consultar as respostas e a evidência.

As fotos dos formulários públicos são armazenadas no PostgreSQL, na tabela `respostas_formularios`. Esse fluxo não usa Google Forms e não depende do login Google do remetente. O envio antigo de fotos pelo painel continua usando o serviço do Drive; as fotos públicas não são sincronizadas para o Drive automaticamente. Inclua o banco na política de backup e dimensionamento de armazenamento.

A migration `V4__formularios_publicos.sql` é aplicada automaticamente pelo Flyway ao iniciar o backend. O frontend usa `/api`; no desenvolvimento há proxy para a porta 8080 e no Docker o Nginx encaminha para `backend:8080`.

## Acesso administrativo e validação

No modo de produção, criar, listar e desativar formulários, ler respostas e visualizar fotos exigem login Google com e-mail verificado presente em `RALLY_ASSISTENTES` (endereços separados por vírgula). A lista vazia bloqueia essas operações. Cabeçalhos `X-Papel` não concedem acesso à administração de formulários em produção. Configure as credenciais Google, o perfil `producao` e essa lista de assistentes no backend.

O ambiente demonstrativo existente libera autenticação e usa os perfis simulados do painel. Essa configuração é destinada ao desenvolvimento local. Os demais endpoints antigos conservam seu mecanismo de perfis existente.

Verificações automatizadas:

- Backend: `mvn test` com Java 25.
- Frontend: `npm run build`.
- Navegador: `npm run test:e2e` após instalar o Chromium do Playwright. Pode-se usar um Chromium já instalado definindo `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`.

Os testes de navegador simulam a API e cobrem publicação, desativação, envio anônimo, link inválido, foto obrigatória e nova tentativa após erro. Os testes unitários do backend cobrem geração dos segredos e validações. A migration e o fluxo completo com banco real devem ser validados no ambiente de implantação.
