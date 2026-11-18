# Integração com o front-end

Este documento acompanha a ligação entre o front (`EquipTrak/frontend`) e esta API. A integração é feita em partes, para que cada PR seja pequeno e fácil de revisar.

## Como o front chama a API

- Todas as chamadas usam o prefixo `/api`. Em desenvolvimento, o `ng serve` repassa esse prefixo para `http://localhost:8080` pelo `proxy.conf.mjs`, então não há CORS no navegador.
- O token do login vai em `Authorization: Bearer <token>` em toda chamada, pelo `authInterceptor`.
- Uma resposta `401` com sessão aberta encerra a sessão no front e volta para o login.
- Os erros seguem o formato descrito em [autenticacao.md](autenticacao.md#formato-dos-erros), lido pelo tipo `ErroApi` do front.

## Andamento

| Parte | Escopo | Situação |
|---|---|---|
| 1 | Login, sessão, logout e perfil do usuário | Feita |
| 2 | Categorias e equipamentos: listar, filtrar, cadastrar e editar | Pendente |
| 3 | Solicitações: criar, listar, aprovar, rejeitar e cancelar | Pendente |
| 4 | Empréstimos: retirada, devolução, atraso e histórico do equipamento | Pendente |
| 5 | Indicadores do painel, histórico por solicitante e bloqueio por atraso | Pendente |

### Parte 1: autenticação

| Tela ou ação do front | Endpoint |
|---|---|
| Formulário de login | `POST /api/auth/login` |
| Recarregar a página com sessão aberta | `GET /api/auth/me` |
| Botão "Sair" | `POST /api/auth/logout` |

- A rota `/painel` só abre com sessão válida (`autenticadoGuard`).
- O perfil e o nome exibidos vêm do backend. Saíram o seletor "Perfil (protótipo)", a senha fixa `demo123` e o perfil na URL (`?perfil=`).
- O perfil `SOLICITANTE` vê as telas de solicitante. Os demais (`OPERADOR`, `ADMINISTRADOR` e `ENCARREGADO`) veem as de operador, até a equipe definir telas próprias.
- Para testar, o perfil `dev` do backend cria usuários de demonstração (lista no [README](../README.md)).

Equipamentos, solicitações e histórico continuam como dados de demonstração no front até as partes 2 a 5.

## Contrato das próximas partes

Endpoints propostos a partir do que as telas do front já fazem com dados de demonstração. Eles servem de ponto de partida e podem mudar na revisão de cada parte.

| Parte | Ação no front (`EquipTrackService`) | Endpoint proposto | Requisitos |
|---|---|---|---|
| 2 | Lista de equipamentos e filtros | `GET /api/equipamentos?situacao=&categoria=&busca=` | RF05, RF19 |
| 2 | `cadastrarEquipamento` | `POST /api/equipamentos` | RF04 |
| 2 | Lista de categorias no formulário | `GET /api/categorias` | RF06 |
| 3 | `criarSolicitacao` | `POST /api/solicitacoes` | RF08 |
| 3 | Fila do operador | `GET /api/solicitacoes?status=` | RF09 |
| 3 | Minhas solicitações | `GET /api/solicitacoes/minhas` | RF18 |
| 3 | `aprovarSolicitacao` | `POST /api/solicitacoes/{id}/aprovacao` | RF09 |
| 3 | `rejeitarSolicitacao` | `POST /api/solicitacoes/{id}/rejeicao` com `{ motivo }` | RF09 |
| 4 | `registrarRetirada` | `POST /api/solicitacoes/{id}/retirada` | RF10 |
| 4 | `registrarDevolucao` | `POST /api/emprestimos/{id}/devolucao` com `{ condicao, observacao }` | RF11, RF12 |
| 4 | Gaveta de histórico | `GET /api/equipamentos/{patrimonio}/historico` | RF15 |
| 5 | Faixa de indicadores | `GET /api/painel/indicadores` | RF20 |

## Divergências entre front e DER

O front foi feito antes do DER final. Estas diferenças precisam de decisão antes da parte correspondente:

| # | Assunto | Front | DER | Proposta |
|---|---|---|---|---|
| 1 | Situação do equipamento | `DISPONIVEL`, `EMPRESTADO`, `INDISPONIVEL` | `DISPONIVEL`, `EMPRESTADO`, `DANIFICADO`, `EM_MANUTENCAO` | A API segue o DER e o front ganha os dois selos novos. No indicador "Indisponíveis", somar `DANIFICADO` e `EM_MANUTENCAO`. |
| 2 | Condição | `OTIMA`, `BOA`, `COM_AVARIA` | Inclui também `REGULAR` | Adicionar `REGULAR` no front. |
| 3 | Status da solicitação | Um único status com `RETIRADO`, `DEVOLVIDO` e `ATRASADO` | Solicitação (`PENDENTE`, `APROVADA`, `REJEITADA`, `CANCELADA`) separada do empréstimo (`ATIVO`, `ATRASADO`, `DEVOLVIDO`) | A API mantém as duas tabelas, mas a listagem devolve um status combinado no formato do front. `ATIVO` vira `RETIRADO`, e o front ganha `CANCELADA`. |
| 4 | Identificadores | Texto, como `S-101` | Número (`BIGINT`) | A API devolve o número, e o front exibe com o prefixo. |
| 5 | Categorias | Lista fixa no código | Tabela `categoria_equipamento` | O front passa a buscar `GET /api/categorias`. |
| 6 | Datas | Texto em `dd/MM/yyyy HH:mm` | `DATETIME` | A API usa ISO-8601, e o front formata para exibir. |
| 7 | Solicitante | Comparado pelo nome | `id_solicitante` | A API devolve `{ id, nome }`, e "minhas solicitações" é filtrada no backend. |
| 8 | Prazo do equipamento | Não existe no formulário | `prazo_max_horas` obrigatório | Adicionar o campo no formulário ou definir um padrão. |
| 9 | Histórico | Tipo em texto livre | `tipo_evento` com valores fixos | A API devolve o tipo fixo, e o front traduz para o texto exibido. |
