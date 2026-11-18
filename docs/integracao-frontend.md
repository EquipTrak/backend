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
| 4 | Empréstimos, por tela do front: 4a retirada, 4b devolução e 4c atraso com bloqueio | Feita |
| 5 | Indicadores do painel (o bloqueio por atraso entrou na 4c) | Feita |

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
- Os eventos são gravados pelo próprio backend, na mesma transação da ação (RN11), e nunca são alterados nem apagados (RN12). Hoje geram evento o cadastro (`POST /api/equipamentos`), a retirada (parte 4a) e a devolução (parte 4b).
- `emprestimoId` vem preenchido nos eventos ligados a um empréstimo, como a retirada. Desde a parte 4a, a coluna é chave estrangeira para `emprestimo`.
- No perfil `dev`, os equipamentos de demonstração ganham o evento de cadastro em nome de Marina Souza, se `EQUIPTRACK_DEV_SENHA` estiver definida.

No front:

- A gaveta busca o histórico ao abrir, com estados de carregando e de erro ("Tentar novamente").
- O front traduz `tipo` para o texto exibido e escolhe o selo (divergência 9, resolvida) e formata `dataHora` no fuso do navegador (divergência 6, para o histórico).
- O solicitante não abre a gaveta: a busca rápida ignora o clique em equipamento para esse perfil.
- Saiu o histórico fixo de demonstração. Desde a parte 4b, todos os eventos da gaveta vêm da API; o front não cria mais eventos locais.

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

### Parte 4a: botão "Registrar retirada" da fila do operador

| Tela ou ação do front | Endpoint | Quem pode |
|---|---|---|
| Modal "Registrar retirada", botão "Confirmar retirada" | `POST /api/solicitacoes/{id}/retirada` | `OPERADOR`, `ENCARREGADO` e `ADMINISTRADOR` |

- Não tem corpo. A resposta é 201, com o cabeçalho `Location` e a solicitação no formato das listas, agora com o empréstimo:

  ```json
  {
    "id": 42,
    "status": "APROVADA",
    "emprestimo": {
      "id": 9,
      "status": "ATIVO",
      "dataRetirada": "2026-11-20T12:05:00Z",
      "dataPrevistaDevolucao": "2026-11-20T15:00:00Z",
      "dataDevolucao": null
    }
  }
  ```

  (os demais campos seguem iguais aos da parte 3a.)

- Na mesma transação, o backend cria o empréstimo, passa o equipamento a `EMPRESTADO` e grava o evento `RETIRADA` no histórico, com `emprestimoId` (RN11).
- A devolução prevista é o fim previsto da solicitação.

  | Regra | Resposta |
  |---|---|
  | Solicitação inexistente | 404 |
  | Solicitação que não está `APROVADA` | 409 |
  | Retirada já registrada para a solicitação | 409 |
  | Fim previsto já passou | 409 em `campos.periodo` |
  | Solicitante com status `BLOQUEADO` | 409 em `campos.solicitante` |
  | Equipamento que não está `DISPONIVEL` (RN02) ou com empréstimo em aberto (RN04) | 409 em `campos.equipamentoId` |
  | Perfil `SOLICITANTE` | 403 |

- Entrou a tabela `emprestimo` do DER. `ativo_flag` vale 1 enquanto o empréstimo está em aberto e fica nulo depois da devolução, e a restrição única `uk_emprestimo_equipamento_ativo` (equipamento, `ativo_flag`) impede, no próprio banco, dois empréstimos em aberto do mesmo equipamento (RN04, RNF06). A retirada também trava a linha do equipamento.
- As listas de solicitações (`/minhas` e a fila) passaram a trazer `emprestimo`, nulo antes da retirada.
- O bloqueio por atraso (RF14) entra na parte 4c.

No front:

- "Confirmar retirada" chama a API e fica desabilitado enquanto ela responde. Os erros aparecem no modal; em 409 e 404, a fila e o catálogo são recarregados.
- O front combina os dois status (divergência 3): empréstimo `ATIVO` aparece como `RETIRADO`, `ATRASADO` como `ATRASADO` e `DEVOLVIDO` como `DEVOLVIDO`. A aba "Em uso" da fila e "Minhas solicitações" mostram as retiradas.
- Saiu o evento local "Retirada registrada" da gaveta: o evento vem do backend.
- No perfil `dev`, o `NB-0002` nasce `EMPRESTADO`; desde a parte 4c ele ganha um empréstimo atrasado de Beatriz Alves.

### Parte 4b: botão "Registrar devolução" da fila do operador

| Tela ou ação do front | Endpoint | Quem pode |
|---|---|---|
| Modal "Registrar devolução", botão "Confirmar devolução" | `POST /api/emprestimos/{id}/devolucao` | `OPERADOR`, `ENCARREGADO` e `ADMINISTRADOR` |

- `{id}` é o id do empréstimo (`emprestimo.id` nas listas de solicitações). Corpo:

  ```json
  { "condicao": "COM_AVARIA", "observacao": "Lente trincada." }
  ```

- A resposta é 200, com a solicitação no formato das listas e `emprestimo.status: "DEVOLVIDO"`.
- Na mesma transação (RN11), o backend:
  - fecha o empréstimo (data, operador, condição e observação da devolução) e libera `ativo_flag`, para que o equipamento possa sair de novo (RN04);
  - grava a condição no equipamento (RN05). Com `COM_AVARIA`, ele fica `DANIFICADO` até a avaliação (RF12, RN06); nos demais casos, volta a `DISPONIVEL`;
  - grava um evento `DEVOLUCAO` no histórico, com situação e condição antes e depois e a observação.

  | Regra | Resposta |
  |---|---|
  | `condicao` ausente ou fora do DER | 400 em `campos.condicao` |
  | `observacao` com mais de 500 caracteres | 400 em `campos.observacao` |
  | Empréstimo inexistente | 404 |
  | Devolução já registrada | 409 |
  | Perfil `SOLICITANTE` | 403 |

- A devolução também vale para empréstimo atrasado. Multa e regularização do atraso ficam para a parte 4c.

No front:

- "Confirmar devolução" chama a API e mostra os erros no modal. O modal ganhou a condição `REGULAR`, e a observação tem limite de 500 caracteres.
- A solicitação vai para `DEVOLVIDO`, e o equipamento muda de situação e condição na hora.
- Saíram os eventos locais de devolução e o mapa de eventos locais do serviço: a gaveta mostra só o que vem da API.

### Parte 4c: atrasos e bloqueio por atraso

Esta parte não tem endpoint novo: muda o que as rotas existentes devolvem e recusam.

- **Atraso calculado (RF13, RN07, RN15).** `emprestimo.status` vem `ATRASADO` quando o empréstimo está em aberto e o dia de hoje já passou do dia da devolução prevista. Devolver no próprio dia não é atraso. O dia é contado no fuso da instituição: `equiptrack.fuso-horario` (variável `FUSO_HORARIO`), com padrão `America/Sao_Paulo`. O banco continua gravando `ATIVO`; o `ATRASADO` só aparece na leitura.
- **Bloqueio por atraso (RF14, RN09).** Enquanto o solicitante tiver empréstimo atrasado:

  | Ação | Resposta |
  |---|---|
  | Criar solicitação (`POST /api/solicitacoes`) | 403, com o motivo em `mensagem` |
  | Aprovar solicitação dele | 409 em `campos.solicitante` |
  | Registrar retirada para ele | 409 em `campos.solicitante` |

- Para regularizar, basta devolver o equipamento: o bloqueio olha só empréstimos ainda em aberto. Nada é gravado em `solicitante.status`, que continua sendo o bloqueio manual.
- A exceção do administrador (RF21), a multa e o alerta de atraso ficam para depois, pois não têm tela no front.
- Bloquear também a criação foi uma escolha, pois o RF14 pede validação: o front já desabilitava "Solicitar" e avisava "Novas solicitações bloqueadas". Se a equipe decidir que a criação deve ser liberada, basta tirar a verificação de `SolicitacaoService.criar`.
- No perfil `dev`, `EmprestimosDevInitializer` dá ao `NB-0002` um empréstimo de Beatriz Alves vencido há dois dias. Assim, a aba "Atrasados" e o bloqueio podem ser testados logo ao subir. Ele só age se o `NB-0002` estiver `EMPRESTADO` sem empréstimo em aberto, então não volta depois da devolução.

No front:

- A aba "Atrasados" da fila, o indicador "Atrasados" do painel e "Minhas solicitações" passam a mostrar os atrasos vindos da API.
- Na tela Disponíveis, o solicitante com atraso vê o aviso "Novas solicitações bloqueadas", e o botão "Solicitar" fica desabilitado. A verificação não compara mais o nome do usuário.

### Parte 5: faixa de indicadores do painel

| Tela ou ação do front | Endpoint | Quem pode |
|---|---|---|
| Faixa "Disponíveis / Emprestados / Atrasados / Indisponíveis" do operador | `GET /api/painel/indicadores` | `OPERADOR`, `ENCARREGADO` e `ADMINISTRADOR` |

- Resposta:

  ```json
  { "disponiveis": 3, "emprestados": 2, "atrasados": 1, "indisponiveis": 1, "total": 6 }
  ```

- Contam só os equipamentos ativos. `indisponiveis` soma `DANIFICADO` e `EM_MANUTENCAO`.
- `atrasados` conta os empréstimos em aberto que já passaram do dia previsto (mesma regra da parte 4c). Como todo atrasado também é emprestado, os quatro totais não somam `total` (RF20).
- O perfil `SOLICITANTE` recebe 403, pois os requisitos dão o dashboard ao operador.

No front:

- O operador busca os totais ao abrir o painel e de novo depois de cadastrar, registrar retirada ou registrar devolução.
- Se a API falhar, e para o solicitante (que também vê a faixa na tela Disponíveis), os totais continuam contados nas listas já carregadas. Para o solicitante, "Atrasados" conta só os dele.

## Fora da integração

Requisitos que ainda não têm tela no front, e por isso ficaram sem endpoint:

- RF03 e RF07: cadastro de usuários e de solicitantes (hoje só os de demonstração, no perfil `dev`).
- RF05: edição de equipamento; RF06: gestão de categorias.
- RF16: histórico por solicitante.
- RF21: exceção do administrador ao bloqueio por atraso.
- RF22 e RF24: avaliação do equipamento devolvido com avaria e correção de registros.
- Tabelas do DER sem uso ainda: `prorrogacao`, `multa`, `bloqueio`, `correcao`, `alerta_atraso` e `visualizacao_alerta`.

## Resumo dos endpoints

Todas as ações que o front fazia com dados de demonstração já usam a API. A tabela reúne o endpoint de cada uma.

| Parte | Ação no front (`EquipTrackService`) | Endpoint | Requisitos |
|---|---|---|---|
| 2a | Lista de equipamentos e filtros | `GET /api/equipamentos?situacao=&categoriaId=&busca=` | RF05, RF19 |
| 2a | `cadastrarEquipamento` | `POST /api/equipamentos` | RF04 |
| 2a | Lista de categorias no formulário | `GET /api/categorias` | RF06 |
| 3a | `criarSolicitacao` | `POST /api/solicitacoes` | RF08 |
| 3c | Fila do operador | `GET /api/solicitacoes?status=` | RF09 |
| 3b | Minhas solicitações | `GET /api/solicitacoes/minhas` | RF18 |
| 3b | `cancelarSolicitacao` | `POST /api/solicitacoes/{id}/cancelamento` | RF23 |
| 3c | `aprovarSolicitacao` | `POST /api/solicitacoes/{id}/aprovacao` | RF09, RN13 |
| 3c | `rejeitarSolicitacao` | `POST /api/solicitacoes/{id}/rejeicao` com `{ motivo }` | RF09 |
| 4a | `registrarRetirada` | `POST /api/solicitacoes/{id}/retirada` | RF10, RN02, RN04 |
| 4b | `registrarDevolucao` | `POST /api/emprestimos/{id}/devolucao` com `{ condicao, observacao }` | RF11, RF12, RN05, RN06 |
| 2b | Gaveta de histórico | `GET /api/equipamentos/{patrimonio}/historico` | RF15 |
| 5 | Faixa de indicadores | `GET /api/painel/indicadores` | RF20 |

## Divergências entre front e DER

O front foi feito antes do DER final. Estas diferenças precisam de decisão antes da parte correspondente:

| # | Assunto | Front | DER | Proposta |
|---|---|---|---|---|
| 1 | Situação do equipamento | `DISPONIVEL`, `EMPRESTADO`, `INDISPONIVEL` | `DISPONIVEL`, `EMPRESTADO`, `DANIFICADO`, `EM_MANUTENCAO` | Resolvida na parte 2a: a API segue o DER e o front ganhou os dois selos novos. O indicador "Indisponíveis" soma `DANIFICADO` e `EM_MANUTENCAO`. |
| 2 | Condição | `OTIMA`, `BOA`, `COM_AVARIA` | Inclui também `REGULAR` | Resolvida na parte 2a: `REGULAR` foi adicionada no front. |
| 3 | Status da solicitação | Um único status com `RETIRADO`, `DEVOLVIDO` e `ATRASADO` | Solicitação (`PENDENTE`, `APROVADA`, `REJEITADA`, `CANCELADA`) separada do empréstimo (`ATIVO`, `ATRASADO`, `DEVOLVIDO`) | Resolvida nas partes 3a e 4a: a API devolve os dois status (`status` e `emprestimo.status`), e o front combina para exibir. `ATIVO` vira `RETIRADO`, e o front ganhou `CANCELADA`. |
| 4 | Identificadores | Texto, como `S-101` | Número (`BIGINT`) | A API devolve o número, e o front exibe com o prefixo. |
| 5 | Categorias | Lista fixa no código | Tabela `categoria_equipamento` | Resolvida na parte 2a: o front busca `GET /api/categorias`. |
| 6 | Datas | Texto em `dd/MM/yyyy HH:mm` | `DATETIME` | A API usa ISO-8601, e o front formata para exibir. |
| 7 | Solicitante | Comparado pelo nome | `id_solicitante` | A API devolve `{ id, nome }`, e "minhas solicitações" é filtrada no backend. |
| 8 | Prazo do equipamento | Não existe no formulário | `prazo_max_horas` obrigatório | Resolvida na parte 2a: o formulário tem o campo, com padrão de 24 horas. |
| 9 | Histórico | Tipo em texto livre | `tipo_evento` com valores fixos | Resolvida na parte 2b: a API devolve o tipo fixo, e o front traduz para o texto exibido. |
