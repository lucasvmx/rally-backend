# Formulários mensais de relatórios

Na aplicação, abra **Administração → Formulários**. Crie um link com um nome (por exemplo, “Relatório mensal — outubro”) e instruções opcionais. A criação não escolhe uma missão: o mesmo link ativo pode receber vários envios para missões diferentes durante o período. Desative o link quando a coleta terminar.

## O que cada envio solicita

Quem abre o link informa:

- Nação e tribo. A tribo disponível é filtrada pela nação escolhida.
- Missão referente àquele relatório.
- Uma foto dos integrantes. O envio exige exatamente uma foto JPEG, PNG ou WebP de até 5 MB.
- Nomes dos integrantes, um por linha.
- Nomes das pessoas novas ou afastadas, um por linha. Se não houver ninguém, deve-se escrever “Nenhum”.
- Nome de quem preencheu.

A quantidade de integrantes e de pessoas novas ou afastadas é calculada pelas linhas com nomes e enviada ao relatório para manter a pontuação existente. Linhas vazias e o texto “Nenhum” não entram na contagem. Cada lista aceita até 200 nomes. O servidor confere as quantidades e valida que a tribo pertence à nação selecionada.

## Link público

O endereço é `/enviar-relatorio/<segredo>`. O backend gera um segredo criptográfico de 256 bits (64 caracteres hexadecimais), independente do identificador do formulário. O link permite abrir a página, mas o envio exige uma sessão autenticada de coordenador de tribo ou de nação. O backend confere se a tribo escolhida está no escopo do coordenador. Assistentes e administradores podem consultar respostas e fotos, mas não enviar relatórios.

A consulta pública do formulário não exige login e não expõe a lista de relatórios nem a revisão administrativa. O envio exige login como coordenador. Links inexistentes e desativados recebem a mesma resposta 404. A desativação impede novas consultas e novos envios, inclusive quando alguém já havia aberto o formulário.

Os dados do formulário e as respostas não são armazenados no cache offline. A tela pública informa aos mecanismos de busca que não deve ser indexada e desabilita o envio do endereço como referência.

## Recebimento e revisão

Cada envio cria um relatório associado à missão e à tribo escolhidas, com status `ENVIADO`, usado pela fila de revisão existente. Os nomes, a pessoa responsável pelo preenchimento e respostas adicionais ficam vinculados ao formulário enviado. Em **Relatórios → Ver envio**, a assistente pode consultar esses dados e visualizar a foto. Coordenadores só enviam relatórios para tribos dentro do próprio escopo.

As fotos dos formulários públicos são armazenadas no PostgreSQL, na tabela `respostas_formularios`. Este fluxo não usa Google Forms e não depende do login Google de quem preenche. As fotos públicas não são sincronizadas para o Google Drive automaticamente. Inclua o banco na política de backup e dimensionamento de armazenamento.

A migration `V4__formularios_publicos.sql` é aplicada automaticamente pelo Flyway ao iniciar o backend. A migration `V9__adicionar_link_google_forms_a_missoes.sql` adiciona à missão o campo opcional `link_google_forms`; o link externo é preservado integralmente e não é consultado pelo backend. O frontend usa `/api`; no desenvolvimento há proxy para a porta 8080 e no Docker o Nginx encaminha para `backend:8080`.

Formulários publicados anteriormente continuam disponíveis. Os que já tinham uma missão associada mantêm essa missão fixa; formulários novos não são associados a uma missão.

## Acesso

Criar, listar e desativar formulários, ler respostas e visualizar fotos exigem role `ASSISTENTE` ou `ADMINISTRADOR_SISTEMA`. Enviar relatórios exige `COORDENADOR_TRIBO` ou `COORDENADOR_NACAO`, e a tribo do relatório precisa pertencer ao escopo autenticado. A role vem do registro de usuário e do JWT; headers enviados pelo cliente não definem o papel nem o escopo. Configure as credenciais Google, o perfil `producao` e os usuários autorizados no backend.

## Verificações automatizadas

- Backend: `mvn test` com Java 25.
- Frontend: `npm run build`.
- Navegador: `npm run test:e2e` após instalar o Chromium do Playwright. Pode-se usar um Chromium já instalado definindo `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`.

Os testes de navegador simulam publicação e desativação do link, envio público, link inválido, foto obrigatória e nova tentativa após erro. Os testes unitários do backend cobrem geração dos segredos e validações. A migration e o fluxo completo com banco real devem ser validados no ambiente de implantação.
