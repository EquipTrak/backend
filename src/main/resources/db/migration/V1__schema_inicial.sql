-- V1: schema inicial do EquipTrack.
-- Gerado a partir do schema que o Hibernate criava com ddl-auto=update (MySQL 8), para que bancos novos e
-- bancos que já existiam (baseline na versão 1) fiquem idênticos. A partir daqui, toda mudança de schema
-- entra como uma nova migration V<n>__descricao.sql; o Hibernate só valida (ddl-auto=validate).

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `categoria_equipamento` (
  `id_categoria` bigint NOT NULL AUTO_INCREMENT,
  `ativa` bit(1) NOT NULL,
  `descricao` varchar(255) DEFAULT NULL,
  `nome` varchar(80) NOT NULL,
  PRIMARY KEY (`id_categoria`),
  UNIQUE KEY `UK56nv7sddks282a0p2a2mqx40l` (`nome`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `emprestimo` (
  `id_emprestimo` bigint NOT NULL AUTO_INCREMENT,
  `ativo_flag` bit(1) DEFAULT NULL,
  `condicao_devolucao` enum('BOA','COM_AVARIA','OTIMA','REGULAR') DEFAULT NULL,
  `data_devolucao` datetime(6) DEFAULT NULL,
  `data_prevista_devolucao` datetime(6) NOT NULL,
  `data_retirada` datetime(6) NOT NULL,
  `observacao_devolucao` varchar(500) DEFAULT NULL,
  `status` enum('ATIVO','ATRASADO','DEVOLVIDO') NOT NULL,
  `id_equipamento` bigint NOT NULL,
  `id_operador_devolucao` bigint DEFAULT NULL,
  `id_operador_retirada` bigint NOT NULL,
  `id_solicitacao` bigint NOT NULL,
  PRIMARY KEY (`id_emprestimo`),
  UNIQUE KEY `UKskjlgxferr4qmwktoqojhhxfo` (`id_solicitacao`),
  UNIQUE KEY `uk_emprestimo_equipamento_ativo` (`id_equipamento`,`ativo_flag`),
  KEY `FKq07hjijfcdpgadccct900grv8` (`id_operador_devolucao`),
  KEY `FKpp0a2og4ira0weigdkw889w2y` (`id_operador_retirada`),
  CONSTRAINT `FKipnp7h9bhnv8hb3r1nep8h7bl` FOREIGN KEY (`id_equipamento`) REFERENCES `equipamento` (`id_equipamento`),
  CONSTRAINT `FKpp0a2og4ira0weigdkw889w2y` FOREIGN KEY (`id_operador_retirada`) REFERENCES `usuario` (`id_usuario`),
  CONSTRAINT `FKq07hjijfcdpgadccct900grv8` FOREIGN KEY (`id_operador_devolucao`) REFERENCES `usuario` (`id_usuario`),
  CONSTRAINT `FKqnaq1vo571ncjx04epuumi97d` FOREIGN KEY (`id_solicitacao`) REFERENCES `solicitacao_emprestimo` (`id_solicitacao`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `equipamento` (
  `id_equipamento` bigint NOT NULL AUTO_INCREMENT,
  `ativo` bit(1) NOT NULL,
  `atualizado_em` datetime(6) NOT NULL,
  `condicao` enum('BOA','COM_AVARIA','OTIMA','REGULAR') NOT NULL,
  `criado_em` datetime(6) NOT NULL,
  `descricao` varchar(255) NOT NULL,
  `marca` varchar(80) DEFAULT NULL,
  `modelo` varchar(80) DEFAULT NULL,
  `numero_serie` varchar(80) DEFAULT NULL,
  `patrimonio` varchar(30) NOT NULL,
  `prazo_max_horas` int NOT NULL,
  `situacao` enum('DANIFICADO','DISPONIVEL','EMPRESTADO','EM_MANUTENCAO') NOT NULL,
  `id_categoria` bigint NOT NULL,
  PRIMARY KEY (`id_equipamento`),
  UNIQUE KEY `UK5j0fo8o49oc6015xatjs1vmh7` (`patrimonio`),
  KEY `FK6pfbm54jds8bdx4po3bmuos81` (`id_categoria`),
  CONSTRAINT `FK6pfbm54jds8bdx4po3bmuos81` FOREIGN KEY (`id_categoria`) REFERENCES `categoria_equipamento` (`id_categoria`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `historico_equipamento` (
  `id_historico` bigint NOT NULL AUTO_INCREMENT,
  `condicao_anterior` enum('BOA','COM_AVARIA','OTIMA','REGULAR') DEFAULT NULL,
  `condicao_nova` enum('BOA','COM_AVARIA','OTIMA','REGULAR') DEFAULT NULL,
  `data_hora` datetime(6) NOT NULL,
  `observacao` varchar(500) DEFAULT NULL,
  `situacao_anterior` enum('DANIFICADO','DISPONIVEL','EMPRESTADO','EM_MANUTENCAO') DEFAULT NULL,
  `situacao_nova` enum('DANIFICADO','DISPONIVEL','EMPRESTADO','EM_MANUTENCAO') DEFAULT NULL,
  `tipo_evento` enum('CADASTRO','CORRECAO','DEVOLUCAO','MANUTENCAO','MUDANCA_CONDICAO','MUDANCA_SITUACAO','RETIRADA') NOT NULL,
  `id_emprestimo` bigint DEFAULT NULL,
  `id_equipamento` bigint NOT NULL,
  `id_usuario` bigint NOT NULL,
  PRIMARY KEY (`id_historico`),
  KEY `FKj0ix6lii9lprqheqjik7ajn1s` (`id_emprestimo`),
  KEY `FK5k7dqxuvup5pru7obt0jxgcva` (`id_equipamento`),
  KEY `FK28j3sljs9qpgt3d95cxulc7lc` (`id_usuario`),
  CONSTRAINT `FK28j3sljs9qpgt3d95cxulc7lc` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`),
  CONSTRAINT `FK5k7dqxuvup5pru7obt0jxgcva` FOREIGN KEY (`id_equipamento`) REFERENCES `equipamento` (`id_equipamento`),
  CONSTRAINT `FKj0ix6lii9lprqheqjik7ajn1s` FOREIGN KEY (`id_emprestimo`) REFERENCES `emprestimo` (`id_emprestimo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `sessao_usuario` (
  `id_sessao` bigint NOT NULL AUTO_INCREMENT,
  `criada_em` datetime(6) NOT NULL,
  `expira_em` datetime(6) NOT NULL,
  `revogada_em` datetime(6) DEFAULT NULL,
  `token_hash` varchar(64) NOT NULL,
  `id_usuario` bigint NOT NULL,
  PRIMARY KEY (`id_sessao`),
  UNIQUE KEY `UK5f6h8beo52nhpbekunuu8mdsq` (`token_hash`),
  KEY `FKh3c66jka0d0elbg91hil2e5ly` (`id_usuario`),
  CONSTRAINT `FKh3c66jka0d0elbg91hil2e5ly` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `solicitacao_emprestimo` (
  `id_solicitacao` bigint NOT NULL AUTO_INCREMENT,
  `data_decisao` datetime(6) DEFAULT NULL,
  `data_fim_prevista` datetime(6) NOT NULL,
  `data_inicio_prevista` datetime(6) NOT NULL,
  `data_solicitacao` datetime(6) NOT NULL,
  `motivo_rejeicao` varchar(255) DEFAULT NULL,
  `status` enum('APROVADA','CANCELADA','PENDENTE','REJEITADA') NOT NULL,
  `id_equipamento` bigint NOT NULL,
  `id_operador_decisao` bigint DEFAULT NULL,
  `id_solicitante` bigint NOT NULL,
  PRIMARY KEY (`id_solicitacao`),
  KEY `FKc1jc0sb6e5snde6lfsghu2jgo` (`id_equipamento`),
  KEY `FK274xnfe8ki4qidu6fhnquogy5` (`id_operador_decisao`),
  KEY `FK3nq8uur44ohw1ovaey9nk7504` (`id_solicitante`),
  CONSTRAINT `FK274xnfe8ki4qidu6fhnquogy5` FOREIGN KEY (`id_operador_decisao`) REFERENCES `usuario` (`id_usuario`),
  CONSTRAINT `FK3nq8uur44ohw1ovaey9nk7504` FOREIGN KEY (`id_solicitante`) REFERENCES `solicitante` (`id_solicitante`),
  CONSTRAINT `FKc1jc0sb6e5snde6lfsghu2jgo` FOREIGN KEY (`id_equipamento`) REFERENCES `equipamento` (`id_equipamento`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `solicitante` (
  `id_solicitante` bigint NOT NULL AUTO_INCREMENT,
  `matricula` varchar(30) NOT NULL,
  `situacao_matricula` enum('ATIVA','INATIVA') NOT NULL,
  `status` enum('ATIVO','BLOQUEADO') NOT NULL,
  `telefone` varchar(20) DEFAULT NULL,
  `tipo` enum('ALUNO','PROFESSOR') NOT NULL,
  `id_usuario` bigint NOT NULL,
  PRIMARY KEY (`id_solicitante`),
  UNIQUE KEY `UK89fa8jxxcyrae2u060pgtnv9m` (`matricula`),
  UNIQUE KEY `UKs5km4c40twlhkbjsr4p0uk19h` (`id_usuario`),
  CONSTRAINT `FKtli5mxhc4gro5pj4fm0qdyb36` FOREIGN KEY (`id_usuario`) REFERENCES `usuario` (`id_usuario`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `usuario` (
  `id_usuario` bigint NOT NULL AUTO_INCREMENT,
  `ativo` bit(1) NOT NULL,
  `criado_em` datetime(6) NOT NULL,
  `email` varchar(150) NOT NULL,
  `nome` varchar(120) NOT NULL,
  `perfil` enum('ADMINISTRADOR','OPERADOR','SOLICITANTE') NOT NULL,
  `senha_hash` varchar(255) NOT NULL,
  PRIMARY KEY (`id_usuario`),
  UNIQUE KEY `UK5171l57faosmj8myawaucatdw` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
