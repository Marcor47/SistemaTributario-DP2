-- Solo perfil local: buzón de ROBERTO MEDINA (061177 / Portal061177!).
-- Todos los documentos están marcados como ejemplos. No se ejecuta Niubiz ni se modifican pagos de SAMNET.
-- La primera lectura y los documentos existentes se conservan al repetir la semilla.
DO $demo$
DECLARE
    v_destinatario VARCHAR := '45871777';
    v_actor BIGINT;
    v_version BIGINT;
    v_solicitud BIGINT;
    v_predio BIGINT;
    v_pdf BYTEA := decode('JVBERi0xLjQKJZOMi54gUmVwb3J0TGFiIEdlbmVyYXRlZCBQREYgZG9jdW1lbnQgKG9wZW5zb3VyY2UpCjEgMCBvYmoKPDwKL0YxIDIgMCBSIC9GMiAzIDAgUgo+PgplbmRvYmoKMiAwIG9iago8PAovQmFzZUZvbnQgL0hlbHZldGljYSAvRW5jb2RpbmcgL1dpbkFuc2lFbmNvZGluZyAvTmFtZSAvRjEgL1N1YnR5cGUgL1R5cGUxIC9UeXBlIC9Gb250Cj4+CmVuZG9iagozIDAgb2JqCjw8Ci9CYXNlRm9udCAvSGVsdmV0aWNhLUJvbGQgL0VuY29kaW5nIC9XaW5BbnNpRW5jb2RpbmcgL05hbWUgL0YyIC9TdWJ0eXBlIC9UeXBlMSAvVHlwZSAvRm9udAo+PgplbmRvYmoKNCAwIG9iago8PAovQ29udGVudHMgOCAwIFIgL01lZGlhQm94IFsgMCAwIDU5NS4yNzU2IDg0MS44ODk4IF0gL1BhcmVudCA3IDAgUiAvUmVzb3VyY2VzIDw8Ci9Gb250IDEgMCBSIC9Qcm9jU2V0IFsgL1BERiAvVGV4dCAvSW1hZ2VCIC9JbWFnZUMgL0ltYWdlSSBdCj4+IC9Sb3RhdGUgMCAvVHJhbnMgPDwKCj4+IAogIC9UeXBlIC9QYWdlCj4+CmVuZG9iago1IDAgb2JqCjw8Ci9QYWdlTW9kZSAvVXNlTm9uZSAvUGFnZXMgNyAwIFIgL1R5cGUgL0NhdGFsb2cKPj4KZW5kb2JqCjYgMCBvYmoKPDwKL0F1dGhvciAoYW5vbnltb3VzKSAvQ3JlYXRpb25EYXRlIChEOjIwMDAwMTAxMDAwMDAwKzAwJzAwJykgL0NyZWF0b3IgKGFub255bW91cykgL0tleXdvcmRzICgpIC9Nb2REYXRlIChEOjIwMDAwMTAxMDAwMDAwKzAwJzAwJykgL1Byb2R1Y2VyIChSZXBvcnRMYWIgUERGIExpYnJhcnkgLSBcKG9wZW5zb3VyY2VcKSkgCiAgL1N1YmplY3QgKHVuc3BlY2lmaWVkKSAvVGl0bGUgKERvY3VtZW50byBkZSBwcnVlYmEgLSBCdXpvbiBIVTAxOSkgL1RyYXBwZWQgL0ZhbHNlCj4+CmVuZG9iago3IDAgb2JqCjw8Ci9Db3VudCAxIC9LaWRzIFsgNCAwIFIgXSAvVHlwZSAvUGFnZXMKPj4KZW5kb2JqCjggMCBvYmoKPDwKL0ZpbHRlciBbIC9BU0NJSTg1RGVjb2RlIC9GbGF0ZURlY29kZSBdIC9MZW5ndGggOTk0Cj4+CnN0cmVhbQpHYXJuVTlsbyNCJkFAWmNwLi4hIU9vNFYlJm9jUk0nUE4/Nm9IbkQ1LXQ+bkxbW0BIOl4rMSgxVlpWME9ZV2dwWFwsMT10JjpDR1RjImhqLSR0ZGciVyFoOSJXOjBNQ3JJZCJzMyRdWSgkLExUYFAvUTtScGQvSDpqW0ErUWFKMktNaipIZGlOWi8tRjplRXVvZlxdQzckbS5URlRqO1ZvSlRgXV5cNFknZUhsbyokbFtjPltWVFZfbD5UP2lsJDxCRi5pSXNqKk1SKTltKFdiR1VdL3ArIkFjKldoKF80Pjc+VFhQXDI4Q007Nj8zOylPTTo4XTphbSdVOmpILGBmcFw2N0cyNWEzOypaS1M7PSZYc01FVz1zL0MoVF0rdCglRywpa2QlQF4yTUNRZyN1KF9damVBUnIvI28zMCYjOGlURiNvWkQjPktfNGEoIzItNyh0UkVQZ2pucCVBMThlLmlGVSQmcjsrXD1kXURZVGY9REJMJC9eM1tHIVVzJkgwcEZVRiItaEs0ZSNVNSdwQVFcSCEncD5zdSRPKG0+XCMoSl9ZWTVpdDxmXHJsRCNcbTpjMiZtRkFGJiEjVGcmTnBTJ0dLOmBlM15TJV1QU1BkTztcVGgwNFk/Y1RIXGdbY0hUWzRlU0VaUGchQ2xMPyRfIz9LK2o2OWNTcSVHJlMnSUVlPT9NaGxtdCwuI2lRIilNM001clVAJ21fOTpwLkE0NDkqUCgiZF5FRlZCSV5IT0JqQk9dKz1SLCJCYTY8blZlbW5rXz5hKV9uOUdCPS8/WnRWTlNTXEwmV0pjalc0Qk1DLC9OYlBGLzBPLz9TYy85VVIyRUI1NHFySUxrLy9zQTc/I01YZ1tEUEg0WnJUW28zS1EuPD9sSGxLTSQ9bClNb3Rzczw2Y3VNPUJKbjFWVVoqVS5XJkBPYT8uXlBqXUY+QUI+MnNAPG5NTWpKRTw0OVU7YGQyX183JCVHWlYsUSs7TjFkS0JCW3AjPEUuc0RBPW9XWXUlQSxjYTg5cyonSFg/bSI5SSk4ZCk3XSQ5NEsqX0c9InVQI1haMStoJjdjTC5dPmFyTGMwQjU4ZURoQTtOU19YQWs6VlVtOi5laWFhMiE4STtkUyJUJGNAMjVzRWE4c0VAPmsrZ0k5Ziw6VkZQRklVNjJGU2Ijb28+S04xTk0vZUhuS2hdN2pib2ssbzMhVEA9ajInKVs5c1U1SyQ/PlNkaD0nWG1cJl45c0NhVkBrTF02MTAkWFZYPWxQUEEiJGlnMig3az9OSyYibUZUT0FnT1RdcCYlNytELGowIjBaNT9HIm0kaX4+ZW5kc3RyZWFtCmVuZG9iagp4cmVmCjAgOQowMDAwMDAwMDAwIDY1NTM1IGYgCjAwMDAwMDAwNjEgMDAwMDAgbiAKMDAwMDAwMDEwMiAwMDAwMCBuIAowMDAwMDAwMjA5IDAwMDAwIG4gCjAwMDAwMDAzMjEgMDAwMDAgbiAKMDAwMDAwMDUyNCAwMDAwMCBuIAowMDAwMDAwNTkyIDAwMDAwIG4gCjAwMDAwMDA4NzggMDAwMDAgbiAKMDAwMDAwMDkzNyAwMDAwMCBuIAp0cmFpbGVyCjw8Ci9JRCAKWzw5NmQ4MTdiOGQzZmRkYzIzNjY2MjgwNWQyM2E4ZjkxND48OTZkODE3YjhkM2ZkZGMyMzY2NjI4MDVkMjNhOGY5MTQ+XQolIFJlcG9ydExhYiBnZW5lcmF0ZWQgUERGIGRvY3VtZW50IC0tIGRpZ2VzdCAob3BlbnNvdXJjZSkKCi9JbmZvIDYgMCBSCi9Sb290IDUgMCBSCi9TaXplIDkKPj4Kc3RhcnR4cmVmCjIwMjEKJSVFT0YK', 'base64');
BEGIN
    IF NOT EXISTS (SELECT 1 FROM samnet.contribuyente WHERE codigo = '061177' AND numero_documento = v_destinatario) THEN
        RETURN;
    END IF;
    SELECT id_usuario INTO v_actor FROM seguridad.usuario WHERE nombre_usuario = 'sistema.constancia';
    SELECT v.id_version INTO v_version FROM operativo.version_plantilla v
    JOIN operativo.plantilla_documento p ON p.id_plantilla = v.id_plantilla
    WHERE p.codigo = 'CONSTANCIA_DEMO' AND v.vigente;

    SELECT id_solicitud INTO v_solicitud FROM operativo.constancia WHERE numero_constancia = 'CNA-DEMO-HU019-01';
    IF v_solicitud IS NULL THEN
        INSERT INTO operativo.solicitud_constancia (canal, estado, fecha_solicitud, documento_contribuyente,
                id_operador, fecha_revision)
        VALUES ('APP', 'EMITIDA', TIMESTAMP '2026-10-14 09:00', v_destinatario, v_actor, TIMESTAMP '2026-10-14 10:00')
        RETURNING id_solicitud INTO v_solicitud;
        INSERT INTO operativo.constancia (numero_constancia, id_solicitud, id_version_plantilla, fecha_emision,
                predios_incluidos, rango_fechas, archivo_pdf, es_de_prueba, id_trabajador_emisor, contenido_pdf)
        VALUES ('CNA-DEMO-HU019-01', v_solicitud, v_version, TIMESTAMP '2026-10-14 11:00',
                'Ejemplo visual. No acredita una verificacion de deuda.', 'Demostracion HU019',
                'documento-de-prueba-hu019.pdf', TRUE, v_actor, v_pdf);
    END IF;
    INSERT INTO operativo.documento_buzon (documento_contribuyente, tipo, titulo, archivo, fecha_envio, numero_constancia)
    VALUES (v_destinatario, 'CONSTANCIA', 'DEMO - Constancia de ejemplo (PDF)',
            'documento-de-prueba-hu019.pdf', TIMESTAMP '2026-10-14 11:00', 'CNA-DEMO-HU019-01')
    ON CONFLICT (numero_constancia) DO NOTHING;

    SELECT id_solicitud_predio INTO v_predio FROM operativo.documento_buzon
    WHERE documento_contribuyente = v_destinatario AND titulo = 'DEMO - Respuesta a solicitud de predio';
    IF v_predio IS NULL THEN
        INSERT INTO operativo.solicitud_predio (tipo_tramite, tipo_predio, documento_contribuyente, codigo_predio,
                estado, fecha_envio, motivo_rechazo, id_revisor, fecha_revision)
        VALUES ('REGISTRO', 'URBANO', v_destinatario, NULL, 'RECHAZADA', TIMESTAMP '2026-10-15 09:00',
                'EJEMPLO DE PRUEBA: falta una copia legible del documento de titularidad. No corresponde a un tramite real.',
                v_actor, TIMESTAMP '2026-10-16 10:00') RETURNING id_solicitud INTO v_predio;
        INSERT INTO operativo.documento_buzon (documento_contribuyente, tipo, titulo, fecha_envio, id_solicitud_predio)
        VALUES (v_destinatario, 'RESPUESTA_SOLICITUD', 'DEMO - Respuesta a solicitud de predio', TIMESTAMP '2026-10-16 10:00', v_predio);
    END IF;

    INSERT INTO operativo.documento_buzon (documento_contribuyente, tipo, titulo, fecha_envio, fecha_primera_lectura)
    SELECT v_destinatario, 'ESTADO_CUENTA', 'DEMO - Estado de cuenta registrado (sin archivo)',
           TIMESTAMP '2026-10-13 08:00', TIMESTAMP '2026-10-13 08:30'
    WHERE NOT EXISTS (SELECT 1 FROM operativo.documento_buzon WHERE documento_contribuyente = v_destinatario
                      AND titulo = 'DEMO - Estado de cuenta registrado (sin archivo)');
END;
$demo$;
