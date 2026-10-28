-- Accesos exclusivamente para los contribuyentes ficticios de R__datos_prueba_01.
-- Flyway los carga al iniciar el backend junto con el resto de db/datos-prueba.
-- Las claves son públicas para desarrollo; esta carpeta no se carga en producción.
-- No sobrescribe claves existentes ni reactiva credenciales deshabilitadas.
WITH accesos (codigo, documento, clave) AS (
    VALUES
        ('014502', '41028765', 'Portal014502!'),
        ('021390', '20456781239', 'Portal021390!'),
        ('033218', '09215430', 'Portal033218!'),
        ('045067', '20512309876', 'Portal045067!'),
        ('058934', '45871203', 'Portal058934!'),
        ('061177', '45871777', 'Portal061177!')
)
INSERT INTO samnet.credencial_web (contribuyente_codigo, clave_hash, habilitada)
SELECT c.codigo, public.crypt(a.clave, public.gen_salt('bf', 12)), TRUE
FROM accesos a
JOIN samnet.contribuyente c ON c.codigo = a.codigo AND c.numero_documento = a.documento
WHERE c.estado = 'ACTIVO'
  AND NOT EXISTS (
      SELECT 1 FROM samnet.credencial_web w WHERE w.contribuyente_codigo = c.codigo
  )
ON CONFLICT (contribuyente_codigo) DO NOTHING;
