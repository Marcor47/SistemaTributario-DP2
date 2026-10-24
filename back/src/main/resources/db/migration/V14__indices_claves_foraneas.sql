-- PostgreSQL no indexa las claves foráneas automáticamente. Sin índice, cada JOIN por la FK y cada borrado o
-- actualización en la tabla referenciada recorre la tabla completa. Se indexan las FK que no tenían un índice
-- con esa columna al inicio.

CREATE INDEX ix_meta_indicador_usuario ON analitica.meta_indicador (id_usuario_registro);
CREATE INDEX ix_beneficio_contribuyente ON samnet.beneficio_tributario (contribuyente_codigo);
CREATE INDEX ix_pago_beneficio ON samnet.pago (id_beneficio);
CREATE INDEX ix_pago_obligacion_obligacion ON samnet.pago_obligacion (id_obligacion);
CREATE INDEX ix_usuario_rol_rol ON seguridad.usuario_rol (id_rol);
CREATE INDEX ix_sesion_usuario ON seguridad.sesion (id_usuario);
CREATE INDEX ix_registro_auditoria_usuario ON seguridad.registro_auditoria (id_usuario);
CREATE INDEX ix_resultado_regla_regla ON analitica.resultado_regla (id_regla);
CREATE INDEX ix_correspondencia_maestro ON maestros.correspondencia_origen (id_maestro);
CREATE INDEX ix_caso_homologacion_lote ON analitica.caso_homologacion (id_lote);
CREATE INDEX ix_caso_homologacion_candidato ON analitica.caso_homologacion (id_maestro_candidato);
CREATE INDEX ix_decision_homologacion_maestro ON analitica.decision_homologacion (id_maestro, fecha_hora);
CREATE INDEX ix_decision_homologacion_usuario ON analitica.decision_homologacion (id_usuario);
CREATE INDEX ix_version_plantilla_publicador ON operativo.version_plantilla (id_publicador);
CREATE INDEX ix_solicitud_constancia_trabajador ON operativo.solicitud_constancia (id_trabajador);
CREATE INDEX ix_solicitud_constancia_operador ON operativo.solicitud_constancia (id_operador);
CREATE INDEX ix_constancia_trabajador ON operativo.constancia (id_trabajador_emisor);
CREATE INDEX ix_constancia_version_plantilla ON operativo.constancia (id_version_plantilla);
CREATE INDEX ix_solicitud_predio_revisor ON operativo.solicitud_predio (id_revisor);
CREATE INDEX ix_documento_adjunto_solicitud ON operativo.documento_adjunto (id_solicitud);
CREATE INDEX ix_documento_adjunto_requisito ON operativo.documento_adjunto (id_requisito);
CREATE INDEX ix_aviso_correo_lote ON operativo.aviso_correo (id_lote);
CREATE INDEX ix_aviso_correo_destinatario ON operativo.aviso_correo (documento_contribuyente);
CREATE INDEX ix_solicitud_reporte_usuario ON operativo.solicitud_reporte (id_usuario);
CREATE INDEX ix_restriccion_reporte_rol ON operativo.restriccion_reporte (id_rol, tipo_reporte);
CREATE INDEX ix_hecho_cobranza_predio ON analitica.hecho_cobranza (id_predio);
CREATE INDEX ix_valor_indicador_lote ON analitica.valor_indicador (id_lote);
CREATE INDEX ix_alerta_desvio_meta ON analitica.alerta_desvio (id_meta);
CREATE INDEX ix_alerta_desvio_valor ON analitica.alerta_desvio (id_valor);
CREATE INDEX ix_segmento_modelo ON analitica.segmento (id_modelo);
CREATE INDEX ix_segmento_aprobador ON analitica.segmento (id_aprobador);
CREATE INDEX ix_asignacion_segmento_contribuyente ON analitica.asignacion_segmento (seudonimo_contribuyente);
CREATE INDEX ix_resumen_ejecutivo_modelo ON analitica.resumen_ejecutivo (id_modelo);
CREATE INDEX ix_resumen_ejecutivo_revisor ON analitica.resumen_ejecutivo (id_revisor);
CREATE INDEX ix_clasificacion_riesgo_modelo ON analitica.clasificacion_riesgo (id_modelo);
CREATE INDEX ix_traza_ia_modelo ON analitica.traza_ia (id_modelo, fecha_hora);
