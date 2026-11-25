# Sobe o EquipTrack inteiro no Docker (MySQL, backend e front) com um comando, para avaliar o sistema.
#
#   Duplo clique em iniciar.cmd, ou no PowerShell:
#   powershell -ExecutionPolicy Bypass -File .\iniciar.ps1
#
# O que faz:
#   1. Confere o Docker (e abre o Docker Desktop, se estiver fechado).
#   2. Baixa o repositório do front ao lado deste, se ainda não estiver lá.
#   3. Na primeira vez, cria o .env com senhas aleatórias e os dados de demonstração ligados.
#   4. Constrói e sobe os contêineres e espera todos ficarem saudáveis.
#   5. Mostra o endereço, os usuários e a senha, e abre o navegador.
#
# Pode rodar de novo quando quiser: o .env e os dados do banco são mantidos.

param(
	# Não abre o navegador no fim.
	[switch]$SemNavegador
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Etapa([string]$texto) { Write-Host ''; Write-Host "== $texto" -ForegroundColor Cyan }

function Docker-Respondendo {
	# O docker escreve no stderr quando o serviço está parado; com 'Stop', o PowerShell 5.1 trataria isso como erro.
	$ErrorActionPreference = 'Continue'
	docker info *> $null
	return ($LASTEXITCODE -eq 0)
}

function Gerar-Segredo([int]$tamanho) {
	$letras = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'.ToCharArray()
	$bytes = New-Object byte[] $tamanho
	(New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($bytes)
	return -join ($bytes | ForEach-Object { $letras[$_ % $letras.Length] })
}

function Ler-Env([string]$chave) {
	$linha = Get-Content .env | Where-Object { $_ -match "^\s*$chave\s*=" } | Select-Object -Last 1
	if ($linha) { return ($linha -replace "^\s*$chave\s*=\s*", '').Trim() }
	return ''
}

# ---------------------------------------------------------------- 1. Docker
Etapa 'Docker'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
	throw 'Docker não encontrado. Instale o Docker Desktop (https://www.docker.com/products/docker-desktop/), abra-o uma vez e rode este script de novo.'
}
if (-not (Docker-Respondendo)) {
	$desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
	if (-not (Test-Path $desktop)) { throw 'O Docker não está rodando. Abra o Docker Desktop e rode este script de novo.' }
	Write-Host 'Abrindo o Docker Desktop (pode levar um ou dois minutos)...'
	Start-Process $desktop
	$limite = (Get-Date).AddMinutes(4)
	while (-not (Docker-Respondendo)) {
		if ((Get-Date) -gt $limite) { throw 'O Docker Desktop não ficou pronto. Abra-o, espere ele dizer que está rodando e rode este script de novo.' }
		Start-Sleep -Seconds 3
	}
}
Write-Host 'Docker rodando.'

# ---------------------------------------------------------------- 2. Front
Etapa 'Repositório do front'
$front = Join-Path (Split-Path $PSScriptRoot -Parent) 'frontend'
if (Test-Path (Join-Path $front 'Dockerfile')) {
	Write-Host "Usando $front"
}
elseif (Test-Path $front) {
	throw "A pasta $front existe, mas não é o repositório do front. Renomeie-a e rode o script de novo."
}
else {
	if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
		throw "Git não encontrado. Instale o Git ou baixe https://github.com/EquipTrak/frontend para $front e rode de novo."
	}
	Write-Host "Baixando o front em $front..."
	git clone --quiet https://github.com/EquipTrak/frontend.git $front
	if ($LASTEXITCODE -ne 0) { throw 'Não foi possível baixar o repositório do front.' }
}

# ---------------------------------------------------------------- 3. .env
Etapa 'Configuração (.env)'
if (Test-Path .env) {
	Write-Host 'Usando o .env que já existe.'
}
else {
	$conteudo = @(
		'# Criado pelo iniciar.ps1 com senhas aleatórias. Não vai para o Git. Modelo e explicações: .env.example.'
		"MYSQL_ROOT_PASSWORD=$(Gerar-Segredo 24)"
		"DB_SENHA=$(Gerar-Segredo 24)"
		"EQUIPTRACK_CHAVE_DESENVOLVEDOR=$(Gerar-Segredo 32)"
		'# Dados de demonstração (usuários, equipamentos e um empréstimo atrasado). É a mesma senha do'
		'# administrador criado pela migration V3 e está no README, para facilitar a avaliação.'
		'EQUIPTRACK_DEMO_SENHA=EquipTrack@2026'
	) -join "`n"
	# Sem BOM e com quebras de linha LF, como o Docker Compose espera.
	[IO.File]::WriteAllText((Join-Path $PSScriptRoot '.env'), $conteudo + "`n", (New-Object Text.UTF8Encoding $false))
	Write-Host '.env criado com senhas aleatórias e os dados de demonstração ligados.'
}

# ---------------------------------------------------------------- 4. Contêineres
Etapa 'Construindo e subindo os contêineres (na primeira vez leva alguns minutos)'
docker compose up -d --build --wait --wait-timeout 900
if ($LASTEXITCODE -ne 0) {
	throw 'Os contêineres não ficaram saudáveis. Veja o motivo com: docker compose logs backend (ou frontend, mysql).'
}

# ---------------------------------------------------------------- 5. Pronto
$porta = Ler-Env 'FRONTEND_PORT'
if (-not $porta) { $porta = '4200' }
$url = "http://localhost:$porta"
$senha = Ler-Env 'EQUIPTRACK_DEMO_SENHA'

Write-Host ''
Write-Host "EquipTrack no ar: $url" -ForegroundColor Green
Write-Host ''
if ($senha) {
	Write-Host "Senha de todos os usuários de demonstração: $senha" -ForegroundColor Yellow
	Write-Host '  admin@equiptrack.local          Administrador'
	Write-Host '  marina.souza@equiptrack.local   Operador (aprova, registra retirada e devolução)'
	Write-Host '  rafael.lima@equiptrack.local    Solicitante'
	Write-Host '  beatriz.alves@equiptrack.local  Solicitante (começa com o NB-0002 em atraso)'
}
else {
	Write-Host 'Sem EQUIPTRACK_DEMO_SENHA no .env: só existe o administrador da migration V3.' -ForegroundColor Yellow
	Write-Host '  admin@equiptrack.local  (senha no README, se ainda não foi trocada)'
}
Write-Host ''
Write-Host 'Para parar:            docker compose stop'
Write-Host 'Para apagar tudo:      docker compose down -v   (apaga também os dados do banco)'

if (-not $SemNavegador) { Start-Process $url }
