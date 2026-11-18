-- V2: categorias iniciais de equipamento. Ainda não há API para categorias; sem elas não dá para cadastrar
-- equipamentos. Cada linha só entra se a categoria ainda não existir, então não mexe em bancos já em uso.

INSERT INTO categoria_equipamento (nome, descricao, ativa)
SELECT nova.nome, nova.descricao, 1
FROM (
    SELECT 'Notebook' AS nome, 'Notebooks para aulas e atividades em campo' AS descricao
    UNION ALL SELECT 'Projetor' AS nome, 'Projetores multimídia' AS descricao
    UNION ALL SELECT 'Câmera' AS nome, 'Câmeras fotográficas e de vídeo' AS descricao
    UNION ALL SELECT 'Adaptador' AS nome, 'Adaptadores e cabos (HDMI, VGA, USB-C)' AS descricao
    UNION ALL SELECT 'Kit' AS nome, 'Kits de iluminação, tripés e acessórios' AS descricao
) AS nova
WHERE NOT EXISTS (SELECT 1 FROM categoria_equipamento c WHERE c.nome = nova.nome);
