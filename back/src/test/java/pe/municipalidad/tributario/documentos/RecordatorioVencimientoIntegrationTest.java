package pe.municipalidad.tributario.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.time.LocalDate;
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
    @MockitoSpyBean CorreoGateway correo;

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
        assertThat(primero.simulados()).isPositive();
        servicio.procesar();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM operativo.aviso_correo
                WHERE tipo = 'RECORDATORIO_VENCIMIENTO' AND id_obligacion = ? AND estado = 'SIMULADO'
                """, Integer.class, id.toString())).isEqualTo(1);
        // Emula un proveedor real sin hacer llamadas externas: el aviso simulado no bloquea la entrega.
        doReturn(false).when(correo).esSimulado();
        servicio.procesar();
        servicio.procesar();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM operativo.aviso_correo
                WHERE tipo = 'RECORDATORIO_VENCIMIENTO' AND id_obligacion = ? AND estado = 'ENVIADO'
                """, Integer.class, id.toString())).isEqualTo(1);
    }
}
