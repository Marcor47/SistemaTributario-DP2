package pe.municipalidad.tributario.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.municipalidad.tributario.integracion.correo.CorreoGateway;
import static org.mockito.Mockito.doReturn;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

@PruebaIntegracion
@Transactional
class RecordatorioVencimientoIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock reloj;
    @Autowired RecordatorioVencimientoService servicio;
    @Autowired PreferenciaAvisoRepository preferencias;
    @MockitoSpyBean CorreoGateway correo;

    @Test
    void respetaLaPreferenciaDelContribuyenteYEnviaAlReactivarla() {
        jdbc.update("""
                INSERT INTO operativo.preferencia_aviso (codigo_contribuyente, recordatorio_vencimiento)
                VALUES ('014502', FALSE)
                """);
        LocalDate vence = LocalDate.now(reloj).plusDays(3);
        Long id = jdbc.queryForObject("""
                INSERT INTO samnet.obligacion (contribuyente_codigo, tributo, anio, numero_cuota,
                    fecha_vencimiento, monto_emitido, monto_pagado)
                VALUES ('014502', 'IMPUESTO_PREDIAL', 2099, 2, ?, 100, 0) RETURNING id
                """, Long.class, vence);
        assertThat(servicio.estadoDe(id).estado()).isEqualTo("DESACTIVADO");
        var desactivado = servicio.procesar();
        assertThat(desactivado.desactivados()).isPositive();
        assertThat(avisosDe(id)).isZero();

        var preferencia = preferencias.findById("014502").orElseThrow();
        preferencia.cambiarRecordatorio(true, LocalDateTime.now(reloj));
        preferencias.saveAndFlush(preferencia);
        servicio.procesar();
        assertThat(avisosDe(id)).isEqualTo(1);
    }

    private int avisosDe(Long id) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM operativo.aviso_correo
                WHERE tipo = 'RECORDATORIO_VENCIMIENTO' AND id_obligacion = ?
                """, Integer.class, id.toString());
    }

    @Test
    void detectaCuotaPendienteYNoRepiteElEnvio() {
        LocalDate vence = LocalDate.now(reloj).plusDays(3);
        Long id = jdbc.queryForObject("""
                INSERT INTO samnet.obligacion (contribuyente_codigo, tributo, anio, numero_cuota,
                    fecha_vencimiento, monto_emitido, monto_pagado)
                VALUES ('014502', 'IMPUESTO_PREDIAL', 2099, 1, ?, 100, 0) RETURNING id
                """, Long.class, vence);
        var primero = servicio.procesar();
        assertThat(primero.enviados()).isZero();
        assertThat(primero.registrados()).isPositive();
        servicio.procesar();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM operativo.aviso_correo
                WHERE tipo = 'RECORDATORIO_VENCIMIENTO' AND id_obligacion = ? AND estado = 'REGISTRADO'
                """, Integer.class, id.toString())).isEqualTo(1);
        /* Emula un proveedor real sin hacer llamadas externas: el aviso registrado no bloquea la entrega. */
        doReturn(false).when(correo).registraSinEnviar();
        servicio.procesar();
        servicio.procesar();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM operativo.aviso_correo
                WHERE tipo = 'RECORDATORIO_VENCIMIENTO' AND id_obligacion = ? AND estado = 'ENVIADO'
                """, Integer.class, id.toString())).isEqualTo(1);
    }

    @Test
    void cubreLosEscenariosDelFixtureDeDatosDePrueba() {
        insertarContribuyente("970001", "99000001", "hu016.uno@correo.test");
        insertarContribuyente("970002", "99000002", "hu016.dos@correo.test");
        insertarContribuyente("970003", "99000003", null);
        insertarContribuyente("970004", "99000004", "hu016.cuatro@correo.test");
        insertarContribuyente("970005", "99000005", "hu016.cinco@correo.test");
        insertarContribuyente("970006", "99000006", "hu016.seis@correo.test");
        insertarContribuyente("970007", "99000007", "hu016.siete@correo.test");

        jdbc.update("""
                INSERT INTO operativo.preferencia_aviso (codigo_contribuyente, recordatorio_vencimiento)
                VALUES ('970002', FALSE)
                """);

        insertarObligacion("970001", "IMPUESTO_PREDIAL", 1, 1, 120, 0);
        insertarObligacion("970001", "ARBITRIOS", 1, 3, 80, 0);
        insertarObligacion("970002", "IMPUESTO_PREDIAL", 2, 2, 150, 0);
        insertarObligacion("970003", "IMPUESTO_PREDIAL", 3, 2, 200, 0);
        insertarObligacion("970004", "IMPUESTO_PREDIAL", 4, 2, 250, 0);
        insertarObligacion("970005", "ARBITRIOS", 2, 2, 300, 0);
        insertarObligacion("970006", "IMPUESTO_PREDIAL", 1, 2, 100, 100);
        insertarObligacion("970007", "IMPUESTO_PREDIAL", 2, 6, 180, 0);
        insertarObligacion("970007", "IMPUESTO_PREDIAL", 3, 0, 180, 0);

        insertarAviso("970004", "99000004", "hu016.cuatro@correo.test", "ENVIADO");
        insertarAviso("970005", "99000005", "hu016.cinco@correo.test", "REGISTRADO");

        var resultado = servicio.procesar();

        assertThat(resultado.sinServidorCorreo()).isTrue();
        assertThat(resultado.registrados()).isGreaterThanOrEqualTo(2);
        assertThat(resultado.duplicados()).isGreaterThanOrEqualTo(2);
        assertThat(resultado.desactivados()).isGreaterThanOrEqualTo(1);
        assertThat(resultado.sinCorreo()).isGreaterThanOrEqualTo(1);
        assertThat(avisosDelContribuyente("970001")).isEqualTo(2);
        assertThat(avisosDelContribuyente("970002")).isZero();
        assertThat(avisosDelContribuyente("970003")).isZero();
        assertThat(avisosDelContribuyente("970004")).isEqualTo(1);
        assertThat(avisosDelContribuyente("970005")).isEqualTo(1);
        assertThat(avisosDelContribuyente("970006")).isZero();
        assertThat(avisosDelContribuyente("970007")).isZero();
    }

    private void insertarContribuyente(String codigo, String documento, String correo) {
        jdbc.update("""
                INSERT INTO samnet.contribuyente
                    (codigo, nombre, tipo_persona, tipo_documento, numero_documento, estado, correo)
                VALUES (?, ?, 'NATURAL', 'DNI', ?, 'ACTIVO', ?)
                """, codigo, "Prueba HU016 " + codigo, documento, correo);
    }

    private void insertarObligacion(String codigo, String tributo, int cuota, int dias, int emitido, int pagado) {
        LocalDate vence = LocalDate.now(reloj).plusDays(dias);
        jdbc.update("""
                INSERT INTO samnet.obligacion (contribuyente_codigo, tributo, anio, numero_cuota,
                    fecha_vencimiento, monto_emitido, monto_pagado)
                VALUES (?, ?, 2099, ?, ?, ?, ?)
                """, codigo, tributo, cuota, vence, emitido, pagado);
    }

    private void insertarAviso(String codigo, String documento, String correo, String estado) {
        jdbc.update("""
                INSERT INTO operativo.aviso_correo
                    (tipo, documento_contribuyente, correo_destino, fecha_programada, fecha_envio,
                     estado, id_obligacion)
                SELECT 'RECORDATORIO_VENCIMIENTO', ?, ?, NOW(), NOW(), ?, o.id::text
                FROM samnet.obligacion o
                WHERE o.contribuyente_codigo = ?
                """, documento, correo, estado, codigo);
    }

    private int avisosDelContribuyente(String codigo) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM operativo.aviso_correo a
                JOIN samnet.obligacion o ON o.id::text = a.id_obligacion
                WHERE o.contribuyente_codigo = ?
                """, Integer.class, codigo);
    }
}
