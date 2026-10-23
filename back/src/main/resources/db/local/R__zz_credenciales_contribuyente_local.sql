INSERT INTO seguridad.credencial_contribuyente_local
    (codigo_contribuyente, password_hash, habilitada)
VALUES
    ('014502', '$2a$12$kfzguWhxmhKz1jMHap1KqesZ6QtUXcCv0DyW0Lk45x4JT6Nt2UmR.', TRUE),
    ('021390', '$2a$12$ZCsEfigfkhUFDdfgnwODZu6ZEB/HIPFvJqtMd84VKBXAW29oYgqLi', TRUE),
    ('033218', '$2a$12$P0iigZxeG/AId6rnuiTnVemRbvzSR6I/Fm07kkIptuQ7HQnEg1Lkm', TRUE),
    ('045067', '$2a$12$FEEKXCHwOPVuYONgLkGQMe0aJwo8aEjENdSqrRH0AHxV12qH8Bi0S', TRUE),
    ('058934', '$2a$12$feJ/JA7glZUYq1SsHPgPbee1suk0k.uWIGSU/kI/PFUSW3aorEZOq', TRUE),
    ('061177', '$2a$12$lhGXgs9W26bRreHxlzEZzuBcWiutI4H69QAXNU7hVnBkERGe2Pacy', TRUE),
    ('072405', '$2a$12$xwmX1VEie7Z8.50Mb7sIsezj.3miAagRwYp7rkITp3VVrwKm7xyYe', TRUE)
ON CONFLICT (codigo_contribuyente) DO NOTHING;
