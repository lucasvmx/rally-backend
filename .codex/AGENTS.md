# Agente de Commit e Push

Este repositório usa um agente dedicado para registrar e enviar alterações quando o usuário pedir commit e/ou push ou delegar explicitamente essa etapa ao agente. Não faça commit nem push em tarefas de análise ou implementação que não incluam essa delegação.

## Mensagens de commit

Use o formato Angular / Conventional Commits, com shortcode de emoji opcional:

```text
<tipo>(<escopo opcional>): :<emoji>: <resumo em português>
```

Mantenha os identificadores de tipo convencionais, sem traduzi-los, para permitir automação de changelog e SemVer. Emojis são permitidos; quando usados, prefira um shortcode coerente com a mudança. Escreva o escopo, o resumo, o corpo e os rodapés em português. Prefira resumo curto, no imperativo, sem ponto final e com até 72 caracteres.

Tipos usuais:

- `feat`: funcionalidade nova
- `fix`: correção de defeito
- `perf`: melhoria de desempenho
- `refactor`: reorganização sem mudança de comportamento
- `test`: testes
- `docs`: documentação
- `build`: build ou dependências
- `ci`: integração contínua
- `style`: formatação sem mudança de comportamento
- `chore`: manutenção
- `revert`: reversão

Para SemVer, use `feat` para indicar versão minor, `fix` ou `perf` para patch e uma alteração incompatível para major. Marque alterações incompatíveis com `!` no cabeçalho e explique o impacto no rodapé `BREAKING CHANGE:`; escreva a explicação em português. Os demais tipos não indicam aumento de versão por si só.

Exemplos:

```text
feat(api): :sparkles: permitir autenticação de assistentes
fix(deploy): :bug: aguardar o banco ficar saudável
docs: :books: explicar as variáveis de produção
feat(api)!: :boom: remover o endpoint legado

BREAKING CHANGE: clientes devem usar a nova rota de autenticação.
```

## Commits atômicos

- Faça um commit por mudança lógica, completa e revisável.
- Prefira um arquivo por commit. Inclua poucos arquivos juntos somente quando forem necessários para a mesma mudança funcionar e permanecer consistente, como implementação e teste correspondente ou configuração e documentação diretamente ligadas.
- Se houver temas independentes, divida-os em commits separados, mesmo que tenham sido alterados na mesma tarefa.
- Se uma mudança inseparável exigir mais de três arquivos, mantenha-a em um só commit atômico; nunca crie commits intermediários que deixem o backend inconsistente.
- Antes de preparar cada commit, confira o branch, `git status` e as diferenças staged e unstaged. Prepare os caminhos relacionados explicitamente; não use `git add -A` nem inclua mudanças preexistentes ou sem relação com a solicitação.
- Todo commit deve incluir sign-off e assinatura criptográfica: use `git commit -s -S`. Nunca remova uma dessas opções para contornar falha de assinatura. Se a assinatura não estiver configurada ou falhar, pare antes do commit e informe o que precisa ser configurado.
- Não altere commits existentes com amend ou rebase, a menos que o usuário peça isso expressamente.

## Push

- Depois do commit, envie o branch atual para o upstream já configurado quando a solicitação incluir push ou quando o usuário tiver delegado commit e push a este agente.
- Antes do push, confira que o commit foi assinado e contém o rodapé `Signed-off-by`.
- Nunca use push forçado. Se o branch não tiver upstream, estiver detached ou o push for rejeitado por divergência, não invente destino nem sobrescreva o remoto; relate a situação e peça a informação necessária.
- Ao concluir, informe o hash e a mensagem de cada commit, o branch enviado e qualquer mudança que tenha ficado de fora.
