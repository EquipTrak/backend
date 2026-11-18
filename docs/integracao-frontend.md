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

## Contrato das próximas partes

Endpoints propostos a partir do que as telas do front já fazem com dados de demonstração. Eles servem de ponto de partida e podem mudar na revisão de cada parte.

| Parte | Ação no front (`EquipTrackService`) | Endpoint proposto | Requisitos |
|---|---|---|---|
| 2a | Lista de equipamentos e filtros | `GET /api/equipamentos?situacao=&categoriaId=&busca=` (feito) | RF05, RF19 |
| 2a | `cadastrarEquipamento` | `POST /api/equipamentos` (feito) | RF04 |
| 2a | Lista de categorias no formulário | `GET /api/categorias` (feito) | RF06 |
| 3 | `criarSolicitacao` | `POST /api/solicitacoes` | RF08 |
| 3 | Fila do operador | `GET /api/solicitacoes?status=` | RF09 |
| 3 | Minhas solicitações | `GET /api/solicitacoes/minhas` | RF18 |
| 3 | `aprovarSolicitacao` | `POST /api/solicitacoes/{id}/aprovacao` | RF09 |
| 3 | `rejeitarSolicitacao` | `POST /api/solicitacoes/{id}/rejeicao` com `{ motivo }` | RF09 |
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
