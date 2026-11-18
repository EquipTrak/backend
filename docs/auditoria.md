# Auditoria técnica e correções

Auditoria feita sobre `EquipTrak/backend` (`master`, commit `d255044`) e `EquipTrak/frontend` (`main`, commit `36105c9`), em 2026-11-18. Este documento resume o que foi encontrado e o que mudou; o diagnóstico original, com evidências e linhas, está no relatório da auditoria.

> **Nota sobre as branches.** O roteiro da auditoria citava `main` no backend e `master` no front, mas é o inverso: o backend usa `master` e o front usa `main`.

## Resultado

O código de regras de negócio e de segurança é de boa qualidade; o que impedia o uso em produção era a **maturidade operacional**. Nenhuma vulnerabilidade crítica (injeção, IDOR, XSS, segredo vazado) foi encontrada, e os contratos entre front e back conferem campo a campo.

## Rastreabilidade

| ID | Problema | Sev. | Repo | Correção | Teste / evidência | Status |
|---|---|---|---|---|---|---|
| F-01 | Front em Docker rodava `ng serve` como root | S2 | frontend | Multi-stage com nginx sem root | Contêiner de produção validado: uid 101, health, SPA, cabeçalhos, 502 limpo sem backend | Corrigido |
| F-02 | Schema por `ddl-auto=update`, sem migrations | S2 | backend | Flyway (V1 schema, V2 categorias) no perfil `mysql`; `ddl-auto=validate` | Banco novo e banco pré-existente com dados validados no MySQL | Corrigido |
| F-03 | Devolução concorrente gravava duas vezes | S2 | backend | Trava do empréstimo antes de ler o status | Teste concorrente: falhava 15/15 antes, passa depois; também validado no MySQL | Corrigido |
| F-04 | Bloqueio de login usava o IP do proxy | S3 | ambos | `FORWARD_HEADERS_STRATEGY` e `X-Forwarded-For` sobrescrito no nginx | IP forjado descartado pelo nginx e aceito direto no backend (controle) | Corrigido na configuração do compose |
| F-05 | Chave de desenvolvedor sem proteção adicional | S3 | backend | Mínimo de 16 caracteres e log de uso recusado | Teste unitário | **Parcial**: continua sem limite de tentativas |
| F-06 | Swagger público | S3 | backend | Desligado por padrão (`SWAGGER_ATIVO`) | Testes e verificação no contêiner | Corrigido |
| F-07 | Listagens sem paginação | S3 | ambos | — | — | **Não corrigido** (muda o contrato) |
| F-08 | Sem CI/CD nem Dependabot | S3 | ambos | Workflows e `dependabot.yml` | Sintaxe validada; execução só no GitHub | **Implementado, não executado** |
| F-09 | Sem health check | S3 | ambos | Actuator só com `/health`; health checks no Docker | Contêineres `healthy` | Corrigido (sem métricas nem logs estruturados) |
| F-10 | Dependências do front com advisories | S3 | frontend | Angular 22.2.7, lockfile regenerado | `npm audit`: 11 → 0; build e testes | Corrigido |
| F-11 | `postgres:latest` com credenciais fixas | S3 | backend | Imagem fixada, porta só em 127.0.0.1 | `bootRun` validado | Parcial: senha de desenvolvimento segue fixa |
| F-12 | Docker fragmentado, script inexistente | S3 | ambos | Compose único; referência ao script removida | Instalação em ambiente limpo | Corrigido |
| F-13 | Testes de interface escassos | S3 | frontend | Testes do guard e do interceptor | 60 → 67 testes; mutação detectada | **Parcial**: componentes seguem sem teste |
| F-14 | Solicitação pendente duplicada sob concorrência | S4 | backend | — | — | Não corrigido |
| F-15 | Token em `sessionStorage`; fontes externas | S4 | frontend | CSP no nginx | Verificado no navegador | Parcial: fontes ainda externas |
| F-16 | Formulário sem marca, modelo e série | S4 | frontend | — | — | Pendente de decisão |
| F-17 | Senha: 72 caracteres × 72 bytes | S4 | backend | — | — | Não reproduzido |
| F-18 | Imagens sem versão fixada | S4 | ambos | Versões fixadas | Builds | Corrigido (versão menor fixada, sem digest) |
| F-19 | Retirada antes do início previsto | S4 | backend | — | — | Pendente de decisão |

### Defeitos encontrados durante a correção

Dois defeitos novos apareceram só ao abrir o app de produção no navegador e foram corrigidos no mesmo trabalho: a CSP bloqueava o `onload` inline do CSS do Angular (página sem estilo) e o nginx repassava o `Host` sem a porta, o que fazia o CORS do backend responder 403 no login.

## Pendências

- Paginação das listagens (F-07), por exigir mudança de contrato nos dois repositórios.
- Limite de tentativas na chave de desenvolvedor (F-05) e testes dos componentes do front (F-13).
- Hospedar as fontes localmente (F-15), observabilidade além do health (métricas, logs estruturados, correlação de requisições) e HTTPS.
- Decisões de negócio: marca/modelo/série no cadastro (F-16) e retirada antecipada (F-19).
- Os workflows de CI e o Dependabot precisam rodar no GitHub para serem considerados validados.
- A dockerização foi validada em Windows 11 com Docker Desktop; Linux e macOS não foram testados.
