#!/usr/bin/env bash
# Sobe o EquipTrack inteiro no Docker (MySQL, backend e front) com um comando, para avaliar o sistema.
#
#   ./iniciar.sh
#
# O que faz:
#   1. Confere o Docker.
#   2. Baixa o repositório do front ao lado deste, se ainda não estiver lá.
#   3. Na primeira vez, cria o .env com senhas aleatórias e os dados de demonstração ligados.
#   4. Constrói e sobe os contêineres e espera todos ficarem saudáveis.
#   5. Mostra o endereço, os usuários e a senha.
#
# Pode rodar de novo quando quiser: o .env e os dados do banco são mantidos.
set -euo pipefail
cd "$(dirname "$0")"

etapa() { printf '\n== %s\n' "$1"; }
falha() { printf 'ERRO: %s\n' "$1" >&2; exit 1; }
segredo() { LC_ALL=C tr -dc 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789' </dev/urandom | head -c "$1"; }
ler_env() { grep -E "^[[:space:]]*$1[[:space:]]*=" .env | tail -n 1 | sed -E "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//" | tr -d '\r' || true; }

etapa 'Docker'
command -v docker >/dev/null || falha 'Docker não encontrado. Instale o Docker (Desktop ou Engine com o Compose v2) e rode de novo.'
docker info >/dev/null 2>&1 || falha 'O Docker não está rodando (ou seu usuário não tem acesso a ele). Inicie o Docker e rode de novo.'
echo 'Docker rodando.'

etapa 'Repositório do front'
front="$(cd .. && pwd)/frontend"
if [ -f "$front/Dockerfile" ]; then
  echo "Usando $front"
elif [ -e "$front" ]; then
  falha "A pasta $front existe, mas não é o repositório do front. Renomeie-a e rode de novo."
else
  command -v git >/dev/null || falha "Git não encontrado. Instale o Git ou baixe https://github.com/EquipTrak/frontend para $front."
  echo "Baixando o front em $front..."
  git clone --quiet https://github.com/EquipTrak/frontend.git "$front"
fi

etapa 'Configuração (.env)'
if [ -f .env ]; then
  echo 'Usando o .env que já existe.'
else
  {
    echo '# Criado pelo iniciar.sh com senhas aleatórias. Não vai para o Git. Modelo e explicações: .env.example.'
    echo "MYSQL_ROOT_PASSWORD=$(segredo 24)"
    echo "DB_SENHA=$(segredo 24)"
    echo "EQUIPTRACK_CHAVE_DESENVOLVEDOR=$(segredo 32)"
    echo '# Dados de demonstração (usuários, equipamentos e um empréstimo atrasado), todos com esta senha.'
    echo "EQUIPTRACK_DEMO_SENHA=$(segredo 12)"
  } > .env
  chmod 600 .env
  echo '.env criado com senhas aleatórias e os dados de demonstração ligados.'
fi

etapa 'Construindo e subindo os contêineres (na primeira vez leva alguns minutos)'
docker compose up -d --build --wait --wait-timeout 900 \
  || falha 'Os contêineres não ficaram saudáveis. Veja o motivo com: docker compose logs backend (ou frontend, mysql).'

porta="$(ler_env FRONTEND_PORT)"
senha="$(ler_env EQUIPTRACK_DEMO_SENHA)"
printf '\nEquipTrack no ar: http://localhost:%s\n\n' "${porta:-4200}"
if [ -n "$senha" ]; then
  echo "Senha de todos os usuários de demonstração: $senha"
  echo '  admin@equiptrack.local          Administrador'
  echo '  marina.souza@equiptrack.local   Operador (aprova, registra retirada e devolução)'
  echo '  rafael.lima@equiptrack.local    Solicitante'
  echo '  beatriz.alves@equiptrack.local  Solicitante (começa com o NB-0002 em atraso)'
else
  echo 'O .env não tem EQUIPTRACK_DEMO_SENHA: o banco não tem usuários de demonstração.'
  echo 'Crie o primeiro usuário como explica o README (passo 6 do início rápido).'
fi
printf '\nPara parar:            docker compose stop\n'
echo 'Para apagar tudo:      docker compose down -v   (apaga também os dados do banco)'
