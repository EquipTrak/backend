-- V3: usuário administrador inicial, para que todo banco novo já permita entrar no sistema.
--   E-mail: admin@equiptrack.local
--   Senha:  EquipTrack@2026  (hash BCrypt abaixo; a senha está no README)
-- Em uso real, troque a senha logo após instalar (README, seção "Trocar a senha do administrador").
-- Só entra se ainda não existir um usuário com esse e-mail, então não mexe em bancos já em uso.

INSERT INTO usuario (ativo, criado_em, email, nome, perfil, senha_hash)
SELECT 1, UTC_TIMESTAMP(6), 'admin@equiptrack.local', 'Administrador', 'ADMINISTRADOR',
       '$2a$10$cwMAyo9YAur3EdGgbe7FLuc745ahPIaVCkSvCEUubX9Uo.9WcFQwW'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'admin@equiptrack.local');
