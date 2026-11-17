package pe.municipalidad.tributario.documentos;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.soporte.PruebaInterna;

/*
 * HU016 (RF-022): detalle paginado de los recordatorios procesados en el día. La tabla incluye enviados,
 * registrados y fallidos, y enriquece cada fila con la obligación vigente en SAMNET.
 */
@PruebaInterna
@Transactional
class RecordatorioVencimientoEnviosIntegrationTest {

    private static final String RUTA = "/api/recordatorios-vencimiento/envios";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock reloj;

    @Test
    void paginaLosIntentosDelDiaEIncluyeElDetalleDeLaObligacion() throws Exception {
        LocalDate hoy = LocalDate.now(reloj);
        Long idObligacion = jdbc.queryForObject("""
                INSERT INTO samnet.obligacion (contribuyente_codigo, tributo, anio, numero_cuota,
                    fecha_vencimiento, monto_emitido, monto_pagado)
                VALUES ('014502', 'IMPUESTO_PREDIAL', 2099, 1, ?, 300, 50) RETURNING id
                """, Long.class, hoy.plusDays(3));
        LocalDateTime ahora = LocalDateTime.now(reloj);
        insertarAviso(idObligacion, "ENVIADO", ahora, ahora);
        insertarAviso(idObligacion, "REGISTRADO", ahora.minusMinutes(1), ahora.minusMinutes(1));
        insertarAviso(idObligacion, "FALLIDO", ahora.minusMinutes(2), null);
        /* Un aviso de ayer no debe aparecer en el día consultado. */
        insertarAviso(idObligacion, "ENVIADO", ahora.minusDays(1), ahora.minusDays(1));

        mockMvc.perform(get(RUTA).param("tamanio", "2").param("pagina", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanio").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.envios", hasSize(2)))
                .andExpect(jsonPath("$.envios[0].estado").value("ENVIADO"))
                .andExpect(jsonPath("$.envios[0].documentoContribuyente").value("41028765"))
                .andExpect(jsonPath("$.envios[0].codigoContribuyente").value("014502"))
                .andExpect(jsonPath("$.envios[0].nombre").value("GARCÍA TORRES, MARÍA ELENA"))
                .andExpect(jsonPath("$.envios[0].correoDestino").value("maria.garcia@correo.test"))
                .andExpect(jsonPath("$.envios[0].tributo").value("IMPUESTO_PREDIAL"))
                .andExpect(jsonPath("$.envios[0].fechaVencimiento").value(hoy.plusDays(3).toString()))
                .andExpect(jsonPath("$.envios[0].saldo").value(250.0))
                .andExpect(jsonPath("$.envios[0].fechaEnvio").isNotEmpty());

        mockMvc.perform(get(RUTA).param("tamanio", "2").param("pagina", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envios", hasSize(1)))
                .andExpect(jsonPath("$.envios[0].estado").value("FALLIDO"))
                .andExpect(jsonPath("$.envios[0].fechaEnvio").doesNotExist());
    }

    @Test
    void permiteConsultarOtroDiaYAcotaElTamanioDePagina() throws Exception {
        LocalDateTime ayer = LocalDateTime.now(reloj).minusDays(1);
        Long idObligacion = jdbc.queryForObject("""
                INSERT INTO samnet.obligacion (contribuyente_codigo, tributo, anio, numero_cuota,
                    fecha_vencimiento, monto_emitido, monto_pagado)
                VALUES ('014502', 'IMPUESTO_PREDIAL', 2099, 2, ?, 100, 0) RETURNING id
                """, Long.class, LocalDate.now(reloj).plusDays(3));
        insertarAviso(idObligacion, "ENVIADO", ayer, ayer);

        mockMvc.perform(get(RUTA)
                        .param("fecha", LocalDate.now(reloj).minusDays(1).toString())
                        .param("tamanio", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.tamanio").value(100))
                .andExpect(jsonPath("$.envios", hasSize(1)));
    }

    private void insertarAviso(Long idObligacion, String estado, LocalDateTime programada, LocalDateTime envio) {
        jdbc.update("""
                INSERT INTO operativo.aviso_correo
                    (tipo, documento_contribuyente, correo_destino, fecha_programada, fecha_envio,
                     estado, id_obligacion)
                VALUES ('RECORDATORIO_VENCIMIENTO', '41028765', 'maria.garcia@correo.test', ?, ?, ?, ?)
                """, programada, envio, estado, idObligacion.toString());
    }
}
