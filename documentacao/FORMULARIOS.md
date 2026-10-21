# Formulários mensais de relatórios

Na aplicação, abra **Administração → Formulários**. Escolha o rally, defina um nome (por exemplo, “Relatório mensal — outubro”) e inclua instruções opcionais. O mesmo link ativo pode receber vários envios para missões diferentes cadastradas naquele rally. Desative o link quando a coleta terminar.

## O que cada envio solicita

Quem abre o link informa:

- Nação e tribo. A tribo disponível é filtrada pela nação escolhida e só aparecem tribos ativas.
- Missão da semana e nome de quem preencheu.
- Uma foto de comprovação dos integrantes. O envio exige exatamente uma foto JPEG, PNG ou WebP de até 5 MB.
- Nomes dos integrantes que aparecem na foto, um por linha.
- Nomes dos jovens novos que aparecem na foto, um por linha. Se não houver ninguém, deve-se escrever “Nenhum”.

A quantidade de integrantes e de jovens novos é calculada pelas linhas com nomes e enviada ao relatório para manter a pontuação existente. Linhas vazias e o texto “Nenhum” não entram na contagem. Cada lista aceita até 200 nomes. O servidor confere as quantidades e valida que a tribo pertence à nação selecionada.

## Link público

O endereço é `/enviar-relatorio/<chave>`. O backend gera uma chave aleatória de 256 bits (64 caracteres hexadecimais), independente do identificador do formulário. A mesma chave na URL autoriza consultar as nações com tribos ativas, as tribos ativas e somente as missões cadastradas para o rally escolhido. No envio, o backend confere novamente se a missão pertence ao rally do link, se a tribo está ativa e se ela pertence à nação informada. Qualquer pessoa com o link pode enviar; não precisa entrar com uma conta. A chave é pública e funciona como uma barreira simples contra envios sem conhecer o link, não como um segredo para proteger dados administrativos. O link não dá acesso a respostas, relatórios ou revisões.

A consulta e o envio do formulário não exigem login. O POST continua protegido por CSRF na aplicação web. Links inexistentes e desativados recebem a mesma resposta 404. A desativação impede novas consultas e novos envios, inclusive quando alguém já havia aberto o formulário. Apagar o rally também remove os links associados.

Os dados do formulário e as respostas não são armazenados no cache offline. A tela pública informa aos mecanismos de busca que não deve ser indexada e desabilita o envio do endereço como referência.

## Recebimento e revisão

Cada envio cria um relatório associado à missão e à tribo escolhidas, com status `ENVIADO`, usado pela fila de revisão existente. Os nomes, a pessoa responsável pelo preenchimento e respostas adicionais ficam vinculados ao formulário enviado. Em **Relatórios → Ver envio**, a assistente pode consultar esses dados e visualizar a foto.

As fotos dos formulários públicos são armazenadas no PostgreSQL, na tabela `respostas_formularios`. Este fluxo não usa Google Forms e não depende do login Google de quem preenche. As fotos públicas não são sincronizadas para o Google Drive automaticamente. Inclua o banco na política de backup e dimensionamento de armazenamento.

As migrations de formulários são aplicadas automaticamente pelo Flyway ao iniciar o backend. `V4__formularios_publicos.sql` cria as tabelas, `V14__permitir_exclusao_em_cascata_de_rallies.sql` preserva o vínculo com uma missão fixa, e `V15__associar_formularios_publicos_a_rallies.sql` associa os links ao rally e configura a exclusão em cascata. A migration `V9__adicionar_link_google_forms_a_missoes.sql` adiciona à missão o campo opcional `link_google_forms`; o link externo é preservado integralmente e não é consultado pelo backend. O frontend usa `/api`; no desenvolvimento há proxy para a porta 8080 e no Docker o Nginx encaminha para `backend:8080`.

Formulários publicados anteriormente continuam disponíveis. Os que já tinham uma missão associada mantêm essa missão fixa e passam a usar o rally dessa missão. Formulários antigos sem missão mantêm o escopo legado; novos links precisam selecionar um rally.

## Acesso

Criar, listar e desativar formulários, ler respostas e visualizar fotos exigem role `ASSISTENTE` ou `ADMINISTRADOR_SISTEMA`. Para criar formulários, essa role precisa escolher um rally. O envio público usa a chave da URL e não concede acesso de leitura ou revisão. Configure as credenciais Google, o perfil `producao` e os usuários autorizados no backend.

## Verificações automatizadas

- Backend: `mvn test` com Java 25.
- Frontend: `npm run build`.
- Navegador: `npm run test:e2e` após instalar o Chromium do Playwright. Pode-se usar um Chromium já instalado definindo `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`.

Os testes de navegador simulam publicação e desativação do link, envio público, link inválido, foto obrigatória e nova tentativa após erro. Os testes unitários do backend cobrem geração dos segredos e validações. A migration e o fluxo completo com banco real devem ser validados no ambiente de implantação.
