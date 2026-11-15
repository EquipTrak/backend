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
| 2 | Equipamentos, por tela do front: 2a lista e cadastro, 2b histórico | Feita |
| 3 | Solicitações, por tela do front: 3a criar, 3b minhas solicitações e cancelar, 3c fila do operador, aprovar e rejeitar | Feita |
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

### Parte 2a: tela de Equipamentos do operador

A parte 2 é entregue uma tela por vez. Esta primeira cobre a aba "Painel" do operador (lista, filtros e o modal "Novo equipamento").

| Tela ou ação do front | Endpoint | Quem pode |
|---|---|---|
| Chips de categoria e select do modal | `GET /api/categorias` | Qualquer usuário logado |
| Lista de equipamentos, indicadores e aba Disponíveis | `GET /api/equipamentos?situacao=&categoriaId=&busca=` | Qualquer usuário logado |
| Botão "Salvar equipamento" | `POST /api/equipamentos` | `OPERADOR`, `ENCARREGADO` e `ADMINISTRADOR` |

- `GET /api/categorias` devolve só as categorias ativas, em ordem alfabética: `[{ "id": 1, "nome": "Adaptador" }]`.
- `GET /api/equipamentos` devolve só os equipamentos ativos, em ordem de patrimônio. Os filtros são opcionais e se combinam; `busca` procura no patrimônio e na descrição, sem diferenciar maiúsculas. Uma situação fora do DER (como `INDISPONIVEL`) responde 400.
- Corpo do `POST /api/equipamentos`:

  ```json
  {
    "patrimonio": "NB-0003",
    "categoriaId": 4,
    "descricao": "Notebook HP",
    "condicao": "OTIMA",
    "situacao": "DISPONIVEL",
    "prazoMaxHoras": 24,
    "marca": null, "modelo": null, "numeroSerie": null
  }
  ```

  | Regra | Resposta |
  |---|---|
  | `patrimonio` obrigatório, até 30 caracteres, só letras, números e hífen; é salvo sem espaços e em maiúsculas | 400 |
  | `categoriaId` precisa existir e estar ativa | 400 em `campos.categoriaId` |
  | `descricao` obrigatória (até 255); `condicao` obrigatória | 400 |
  | `situacao` opcional (padrão `DISPONIVEL`); `EMPRESTADO` não é aceito no cadastro | 400 em `campos.situacao` |
  | `prazoMaxHoras` obrigatório, de 1 a 720 | 400 |
  | Patrimônio já cadastrado, mesmo com outra caixa | 409 em `campos.patrimonio` |
  | Perfil `SOLICITANTE` | 403 |

  A resposta é 201, com o cabeçalho `Location` e o equipamento no mesmo formato da listagem.

No front:

- O painel carrega categorias e equipamentos ao abrir. Se a API falhar, a tela mostra o estado de erro, e "Tentar novamente" refaz a busca.
- As divergências 1, 2, 5 e 8 abaixo foram resolvidas assim: o front passou a usar `DANIFICADO` e `EM_MANUTENCAO` (somados em "Indisponíveis"), ganhou a condição `REGULAR`, busca as categorias na API e tem o campo "Prazo máximo de empréstimo (horas)", com padrão de 24.
- Os erros por campo da API (400 e 409) aparecem embaixo de cada campo do modal.
- No perfil `dev`, o backend cria as categorias e os seis equipamentos que o front tinha fixos (`CatalogoDevInitializer`). O `NB-0002` nasce `EMPRESTADO` só para a demonstração; ainda não existe empréstimo ligado a ele.

A edição de equipamento (RF05) ainda não tem tela no front e ficou fora.

### Parte 2b: gaveta de histórico do equipamento

| Tela ou ação do front | Endpoint | Quem pode |
|---|---|---|
| Gaveta "Histórico do equipamento" | `GET /api/equipamentos/{patrimonio}/historico` | `OPERADOR`, `ENCARREGADO` e `ADMINISTRADOR` |

- A resposta traz os eventos do mais recente para o mais antigo:

  ```json
  [
    {
      "id": 7,
      "dataHora": "2026-11-14T15:30:00Z",
      "tipo": "CADASTRO",
      "usuario": { "id": 2, "nome": "Marina Souza" },
      "situacaoAnterior": null,
      "situacaoNova": "DISPONIVEL",
      "condicaoAnterior": null,
      "condicaoNova": "OTIMA",
      "observacao": "Situação inicial definida.",
      "emprestimoId": null
    }
  ]
  ```

- `tipo` usa os valores do DER: `CADASTRO`, `RETIRADA`, `DEVOLUCAO`, `MUDANCA_CONDICAO`, `MUDANCA_SITUACAO`, `MANUTENCAO` e `CORRECAO`.
- O patrimônio é procurado sem diferenciar maiúsculas. Patrimônio inexistente responde 404; perfil `SOLICITANTE` responde 403; equipamento sem eventos devolve `[]`.
- Os eventos são gravados pelo próprio backend, na mesma transação da ação (RN11), e nunca são alterados nem apagados (RN12). Por enquanto só o cadastro (`POST /api/equipamentos`) gera evento; retirada, devolução e os demais entram com as partes 3 e 4.
- `emprestimoId` fica nulo até existir a tabela de empréstimos (parte 4), quando a coluna vira chave estrangeira.
- No perfil `dev`, os equipamentos de demonstração ganham o evento de cadastro em nome de Marina Souza, se `EQUIPTRACK_DEV_SENHA` estiver definida.

No front:

- A gaveta busca o histórico ao abrir, com estados de carregando e de erro ("Tentar novamente").
- O front traduz `tipo` para o texto exibido e escolhe o selo (divergência 9, resolvida) e formata `dataHora` no fuso do navegador (divergência 6, para o histórico).
- O solicitante não abre a gaveta: a busca rápida ignora o clique em equipamento para esse perfil.
- Saiu o histórico fixo de demonstração. Os eventos das ações que ainda são locais (solicitações, retirada e devolução) continuam aparecendo no topo da gaveta até recarregar a página.

### Parte 3a: botão "Solicitar" da tela Disponíveis

| Tela ou ação do front | Endpoint | Quem pode |
|---|---|---|
| Modal "Solicitar empréstimo", botão "Enviar solicitação" | `POST /api/solicitacoes` | `SOLICITANTE` |

- Corpo, com as datas em ISO-8601 (UTC):

  ```json
  {
    "equipamentoId": 3,
    "inicioPrevisto": "2026-11-20T12:00:00Z",
    "fimPrevisto": "2026-11-20T15:00:00Z"
  }
  ```

- A resposta é 201, com o cabeçalho `Location`:

  ```json
  {
    "id": 42,
    "solicitante": { "id": 1, "nome": "Rafael Lima" },
    "equipamento": { "id": 3, "patrimonio": "PJ-0001", "descricao": "Projetor Epson" },
    "dataSolicitacao": "2026-11-15T12:00:00Z",
    "inicioPrevisto": "2026-11-20T12:00:00Z",
    "fimPrevisto": "2026-11-20T15:00:00Z",
    "status": "PENDENTE",
    "motivoRejeicao": null
  }
  ```

- O solicitante é sempre o usuário logado; o corpo não informa quem pede. `solicitante.id` é o `id_solicitante` do DER, não o id do usuário.

  | Regra | Resposta |
  |---|---|
  | `equipamentoId`, `inicioPrevisto` e `fimPrevisto` obrigatórios | 400 |
  | Início no passado (RN10), com folga de 5 minutos para o relógio do formulário | 400 em `campos.inicioPrevisto` |
  | Fim igual ou anterior ao início (RN10) | 400 em `campos.fimPrevisto` |
  | Período maior que o `prazoMaxHoras` do equipamento | 400 em `campos.fimPrevisto` |
  | Equipamento inexistente ou inativo | 400 em `campos.equipamentoId` |
  | Equipamento que não está `DISPONIVEL` (RN02) | 409 em `campos.equipamentoId` |
  | O mesmo solicitante já tem solicitação `PENDENTE` para o equipamento | 409 em `campos.equipamentoId` |
  | Usuário sem cadastro de solicitante, com status `BLOQUEADO` ou matrícula `INATIVA` | 403, com o motivo em `mensagem` |
  | Perfil diferente de `SOLICITANTE` | 403 |

- Criar a solicitação não muda a situação do equipamento (RN14) nem gera evento no histórico: o `tipo_evento` do DER não tem valor para solicitação.
- Dois solicitantes podem pedir o mesmo equipamento; a sobreposição de períodos (RN13) é verificada na aprovação.
- O bloqueio por atraso (RF14) depende da tabela de empréstimos e entra na parte 4. Por ora só o status `BLOQUEADO` do solicitante impede a criação.
- Entraram as tabelas `solicitante` e `solicitacao_emprestimo` do DER. No perfil `dev`, Rafael Lima e Beatriz Alves ganham o cadastro de solicitante (`SolicitantesDevInitializer`), pois ainda não há tela para o RF07.

No front:

- O modal mostra o prazo máximo do equipamento e valida o período antes de enviar. Os erros da API aparecem no campo de início, no de fim ou como mensagem geral.
- A solicitação criada entra no topo de "Minhas solicitações" com o número devolvido pela API (`S-42`).
- O front ganhou o status `CANCELADA` (divergência 3).

### Parte 3b: tela "Minhas solicitações"

| Tela ou ação do front | Endpoint | Quem pode |
|---|---|---|
| Lista "Minhas solicitações" | `GET /api/solicitacoes/minhas` | `SOLICITANTE` |
| Botão "Cancelar solicitação" | `POST /api/solicitacoes/{id}/cancelamento` | `SOLICITANTE`, só a própria |

- `GET /api/solicitacoes/minhas` devolve as solicitações do usuário logado, da mais recente para a mais antiga, no mesmo formato da resposta da criação. Usuário sem cadastro de solicitante recebe `[]`.
- `POST /api/solicitacoes/{id}/cancelamento` não tem corpo e devolve a solicitação com `status: "CANCELADA"` (RF23).

  | Regra | Resposta |
  |---|---|
  | Solicitação inexistente ou de outro solicitante | 404 |
  | Solicitação que não está `PENDENTE` | 409, com o motivo em `mensagem` |
  | Perfil diferente de `SOLICITANTE` | 403 |

- O momento do cancelamento fica em `data_decisao`, e `id_operador_decisao` continua nulo (RN11).
- Depois de cancelar, o solicitante pode pedir o mesmo equipamento de novo.
- `status` ainda traz só o status da solicitação. Os valores `RETIRADO`, `DEVOLVIDO` e `ATRASADO` da divergência 3 entram com a tabela de empréstimos (parte 4).

No front:

- A lista é buscada ao abrir o painel, com estados de carregando e de erro ("Tentar novamente"). Saíram os dados de demonstração do solicitante e o filtro pelo nome (divergência 7, para esta tela).
- Cada solicitação pendente tem o botão "Cancelar solicitação", com confirmação. Se a API responder 409 ou 404, o modal mostra o motivo e a lista é recarregada.

### Parte 3c: fila de solicitações do operador

| Tela ou ação do front | Endpoint | Quem pode |
|---|---|---|
| Abas "Pendentes" e "Aprovadas" da fila | `GET /api/solicitacoes?status=` | `OPERADOR`, `ENCARREGADO` e `ADMINISTRADOR` |
| Botão "Aprovar" | `POST /api/solicitacoes/{id}/aprovacao` | os mesmos |
| Modal "Rejeitar solicitação" | `POST /api/solicitacoes/{id}/rejeicao` com `{ "motivo": "..." }` | os mesmos |

- `GET /api/solicitacoes` devolve as solicitações de todos os solicitantes, da mais antiga para a mais recente (ordem de chegada), no mesmo formato das outras rotas. `status` é opcional; um valor fora do DER responde 400.
- A aprovação não tem corpo. As duas decisões devolvem a solicitação atualizada e gravam quem decidiu (`id_operador_decisao`) e quando (`data_decisao`), conforme a RN11.

  | Regra | Resposta |
  |---|---|
  | Solicitação inexistente | 404 |
  | Solicitação que não está `PENDENTE` | 409 |
  | Aprovação: outra solicitação `APROVADA` do mesmo equipamento com período que se sobrepõe (RN13) | 409 em `campos.periodo` |
  | Aprovação: equipamento `DANIFICADO`, `EM_MANUTENCAO` ou inativo | 409 em `campos.equipamentoId` |
  | Aprovação: solicitante com status `BLOQUEADO` | 409 em `campos.solicitante` |
  | Rejeição: `motivo` vazio ou com mais de 255 caracteres | 400 em `campos.motivo` |
  | Perfil `SOLICITANTE` | 403 |

- Períodos que só se encostam (um termina quando o outro começa) não contam como sobreposição.
- A aprovação trava a linha do equipamento até o fim da transação, para que duas aprovações simultâneas do mesmo equipamento não passem as duas pela RN13 (RNF06).
- Aprovar não é retirar (RN03): a situação do equipamento não muda, e nada é gravado no histórico. Um equipamento `EMPRESTADO` pode ter a próxima solicitação aprovada; a retirada é que espera a devolução.
- O bloqueio por atraso (RF14) entra com a tabela de empréstimos (parte 4).

No front:

- A fila é buscada ao abrir o painel, com estados de carregando e de erro. Saíram os dados de demonstração das solicitações.
- "Aprovar" fica desabilitado enquanto a chamada está em andamento. Um erro aparece em aviso, com o motivo devolvido pela API.
- O modal de rejeição mostra o erro do motivo no campo e os demais embaixo.
- Em 409 e 404, a fila é recarregada para mostrar o status atual.
- As abas "Em uso" e "Atrasados" ficam vazias até a parte 4, pois a API ainda não devolve os status de empréstimo. Retirada e devolução continuam locais.
- Saiu o evento local "Solicitação aprovada" da gaveta de histórico.

## Contrato das próximas partes

Endpoints propostos a partir do que as telas do front já fazem com dados de demonstração. Eles servem de ponto de partida e podem mudar na revisão de cada parte.

| Parte | Ação no front (`EquipTrackService`) | Endpoint proposto | Requisitos |
|---|---|---|---|
| 2a | Lista de equipamentos e filtros | `GET /api/equipamentos?situacao=&categoriaId=&busca=` (feito) | RF05, RF19 |
| 2a | `cadastrarEquipamento` | `POST /api/equipamentos` (feito) | RF04 |
| 2a | Lista de categorias no formulário | `GET /api/categorias` (feito) | RF06 |
| 3a | `criarSolicitacao` | `POST /api/solicitacoes` (feito) | RF08 |
| 3c | Fila do operador | `GET /api/solicitacoes?status=` (feito) | RF09 |
| 3b | Minhas solicitações | `GET /api/solicitacoes/minhas` (feito) | RF18 |
| 3b | `cancelarSolicitacao` | `POST /api/solicitacoes/{id}/cancelamento` (feito) | RF23 |
| 3c | `aprovarSolicitacao` | `POST /api/solicitacoes/{id}/aprovacao` (feito) | RF09, RN13 |
| 3c | `rejeitarSolicitacao` | `POST /api/solicitacoes/{id}/rejeicao` com `{ motivo }` (feito) | RF09 |
| 4 | `registrarRetirada` | `POST /api/solicitacoes/{id}/retirada` | RF10 |
| 4 | `registrarDevolucao` | `POST /api/emprestimos/{id}/devolucao` com `{ condicao, observacao }` | RF11, RF12 |
| 2b | Gaveta de histórico | `GET /api/equipamentos/{patrimonio}/historico` (feito) | RF15 |
| 5 | Faixa de indicadores | `GET /api/painel/indicadores` | RF20 |

## Divergências entre front e DER

O front foi feito antes do DER final. Estas diferenças precisam de decisão antes da parte correspondente:

| # | Assunto | Front | DER | Proposta |
|---|---|---|---|---|
| 1 | Situação do equipamento | `DISPONIVEL`, `EMPRESTADO`, `INDISPONIVEL` | `DISPONIVEL`, `EMPRESTADO`, `DANIFICADO`, `EM_MANUTENCAO` | Resolvida na parte 2a: a API segue o DER e o front ganhou os dois selos novos. O indicador "Indisponíveis" soma `DANIFICADO` e `EM_MANUTENCAO`. |
| 2 | Condição | `OTIMA`, `BOA`, `COM_AVARIA` | Inclui também `REGULAR` | Resolvida na parte 2a: `REGULAR` foi adicionada no front. |
| 3 | Status da solicitação | Um único status com `RETIRADO`, `DEVOLVIDO` e `ATRASADO` | Solicitação (`PENDENTE`, `APROVADA`, `REJEITADA`, `CANCELADA`) separada do empréstimo (`ATIVO`, `ATRASADO`, `DEVOLVIDO`) | A API mantém as duas tabelas, mas a listagem devolve um status combinado no formato do front. `ATIVO` vira `RETIRADO`, e o front ganha `CANCELADA`. |
| 4 | Identificadores | Texto, como `S-101` | Número (`BIGINT`) | A API devolve o número, e o front exibe com o prefixo. |
| 5 | Categorias | Lista fixa no código | Tabela `categoria_equipamento` | Resolvida na parte 2a: o front busca `GET /api/categorias`. |
| 6 | Datas | Texto em `dd/MM/yyyy HH:mm` | `DATETIME` | A API usa ISO-8601, e o front formata para exibir. |
| 7 | Solicitante | Comparado pelo nome | `id_solicitante` | A API devolve `{ id, nome }`, e "minhas solicitações" é filtrada no backend. |
| 8 | Prazo do equipamento | Não existe no formulário | `prazo_max_horas` obrigatório | Resolvida na parte 2a: o formulário tem o campo, com padrão de 24 horas. |
| 9 | Histórico | Tipo em texto livre | `tipo_evento` com valores fixos | Resolvida na parte 2b: a API devolve o tipo fixo, e o front traduz para o texto exibido. |
