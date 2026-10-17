# Instruções do repositório

Leia e siga as instruções completas do agente de commit e push em [`.codex/AGENTS.md`](.codex/AGENTS.md) em todas as tarefas neste repositório.

## Delegação multiagentes

Os perfis do Codex para este backend ficam em `.codex/agents/`:

- **Staff Software Engineer** coordena o desenho técnico, define tarefas e contratos, implementa ou integra a lógica de negócio e mantém a visão do backend como um todo.
- **Security Engineer** deve ser acionado para mudanças em autenticação, autorização, formulários públicos, tokens, dados pessoais, arquivos, CORS ou segredos.
- **Site Reliability Engineer** deve ser acionado para mudanças em implantação, Docker, configuração de produção, banco e migrations, disponibilidade, observabilidade, desempenho ou recuperação de falhas.

Use os especialistas sob demanda e com tarefas delimitadas. Não mantenha mais de dois subagentes ativos ao mesmo tempo. Evite atribuir alterações concorrentes nos mesmos arquivos; o Staff Software Engineer integra os resultados e reporta decisões ou riscos pendentes.
