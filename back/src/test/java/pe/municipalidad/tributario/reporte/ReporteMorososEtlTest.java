package pe.municipalidad.tributario.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.seguridad.ClaveInternaService;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU006 y HU018: camino ETL del reporte de morosos. Los montos se calculan sobre
 * analitica.hecho_cobranza y la identidad y la etapa se completan desde SAMNET usando
 * maestros.correspondencia_origen (RNF-018). El seed R__datos_prueba_10 provee dimensiones,
 * correspondencia y una carga base; cada test crea su propio lote con hechos acotados.
 */
@PruebaIntegracion
@Transactional
class ReporteMorososEtlTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private RolRepository roles;
    @Autowired
    private ClaveInternaService claves;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void morososSeCalculanSobreElEtlYSeEnriquecenConSamnet() throws Exception {
        String seudonimo = seudonimoContribuyente("021390");
        Long idLote = insertarCargaEtl();
        Long idPredio = predio("021390-01");
        insertarHecho(idLote, seudonimo, idPredio, "IMPUESTO_PREDIAL", 2026, 1, LocalDate.of(2026, 3, 31),
                new BigDecimal("1010.88"), new BigDecimal("1010.88"));
        insertarHecho(idLote, seudonimo, idPredio, "ARBITRIOS", 2026, 2, LocalDate.of(2026, 6, 30),
                new BigDecimal("1315.60"), new BigDecimal("1315.60"));

        mvc.perform(get("/api/reportes/MOROSOS").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaCorte").value("2026-10-19"))
                .andExpect(jsonPath("$.resumen.cantidad").value(1))
                .andExpect(jsonPath("$.resumen.montoTotal").value(2326.48))
                .andExpect(jsonPath("$.filas", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].codigo").value("021390"))
                .andExpect(jsonPath("$.filas[0].nombre").value("INVERSIONES COSTA AZUL S.A.C."))
                .andExpect(jsonPath("$.filas[0].tipoDocumento").value("RUC"))
                .andExpect(jsonPath("$.filas[0].numeroDocumento").value("20456781239"))
                .andExpect(jsonPath("$.filas[0].regimen").value("GENERAL"))
                .andExpect(jsonPath("$.filas[0].zonas", contains("Z1", "Z2", "Z3")))
                .andExpect(jsonPath("$.filas[0].deudaVencida").value(2326.48))
                .andExpect(jsonPath("$.filas[0].deudaSinBeneficio").value(2326.48))
                .andExpect(jsonPath("$.filas[0].cuotasVencidas").value(2))
                .andExpect(jsonPath("$.filas[0].vencimientoMasAntiguo").value("2026-03-31"))
                .andExpect(jsonPath("$.filas[0].diasVencido").value(202))
                .andExpect(jsonPath("$.filas[0].etapaCobranza").value("VALOR_EMITIDO"))
                .andExpect(jsonPath("$.filas[0].deudaPorTributo", hasSize(2)))
                .andExpect(jsonPath("$.filas[0].detalleDeuda", hasSize(2)))
                .andExpect(jsonPath("$.filas[0].detalleDeuda[*].codigoPredio", hasItem("021390-01")))
                // La zona del detalle solo existe para arbitrios.
                .andExpect(jsonPath("$.filas[0].detalleDeuda[0].tributo").value("IMPUESTO_PREDIAL"))
                .andExpect(jsonPath("$.filas[0].detalleDeuda[0].zona").value(nullValue()))
                .andExpect(jsonPath("$.filas[0].detalleDeuda[1].tributo").value("ARBITRIOS"))
                .andExpect(jsonPath("$.filas[0].detalleDeuda[1].zona").value("Z2"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM seguridad.registro_auditoria WHERE accion = 'REIDENTIFICAR'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void elFiltroDeZonaSoloSumaLosArbitriosDeLaZona() throws Exception {
        String seudonimo = seudonimoContribuyente("021390");
        Long idLote = insertarCargaEtl();
        Long idPredio = predio("021390-01");
        insertarHecho(idLote, seudonimo, idPredio, "IMPUESTO_PREDIAL", 2026, 1, LocalDate.of(2026, 3, 31),
                new BigDecimal("1010.88"), new BigDecimal("1010.88"));
        insertarHecho(idLote, seudonimo, idPredio, "ARBITRIOS", 2026, 2, LocalDate.of(2026, 6, 30),
                new BigDecimal("1315.60"), new BigDecimal("1315.60"));

        mvc.perform(get("/api/reportes/MOROSOS").param("zona", "Z2")
                        .session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.cantidad").value(1))
                .andExpect(jsonPath("$.resumen.montoTotal").value(1315.60))
                .andExpect(jsonPath("$.filas[0].deudaVencida").value(1315.60))
                .andExpect(jsonPath("$.filas[0].deudaSinBeneficio").value(1315.60))
                .andExpect(jsonPath("$.filas[0].cuotasVencidas").value(1))
                .andExpect(jsonPath("$.filas[0].deudaPorTributo", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].deudaPorTributo[0].tributo").value("ARBITRIOS"))
                .andExpect(jsonPath("$.filas[0].detalleDeuda", hasSize(1)))
                .andExpect(jsonPath("$.filas[0].detalleDeuda[0].zona").value("Z2"));
    }

    @Test
    void elFiltroDeBeneficioSeAplicaSobreLosHechosDelEtl() throws Exception {
        String seudonimo = seudonimoContribuyente("021390");
        Long idLote = insertarCargaEtl();
        Long idPredio = predio("021390-01");
        insertarHecho(idLote, seudonimo, idPredio, "IMPUESTO_PREDIAL", 2026, 1, LocalDate.of(2026, 3, 31),
                new BigDecimal("1000.00"), new BigDecimal("900.00"));
        MockHttpSession gerencia = sesion(NombreRol.GERENCIA);

        mvc.perform(get("/api/reportes/MOROSOS").param("beneficio", "SIN_BENEFICIO")
                        .session(gerencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.cantidad").value(0));

        mvc.perform(get("/api/reportes/MOROSOS").param("beneficio", "CON_BENEFICIO")
                        .session(gerencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.cantidad").value(1))
                .andExpect(jsonPath("$.filas[0].deudaVencida").value(900.00))
                .andExpect(jsonPath("$.filas[0].deudaSinBeneficio").value(1000.00));
    }

    @Test
    void sinCargaEtlConHechosDevuelveErrorDeNegocio() throws Exception {
        jdbc.update("DELETE FROM analitica.hecho_cobranza");

        mvc.perform(get("/api/reportes/MOROSOS").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_CARGA_ETL"));
    }

    @Test
    void sinCorrespondenciaDevuelveErrorDeNegocio() throws Exception {
        Long idLote = insertarCargaEtl();
        String seudonimo = "etl-sin-correspondencia";
        jdbc.update("""
                INSERT INTO analitica.dim_contribuyente
                    (seudonimo_contribuyente, tipo_persona, regimen, condicion)
                VALUES (?, 'JURIDICA', 'GENERAL', 'HABIDO')
                """, seudonimo);
        Long idPredio = jdbc.queryForObject("""
                INSERT INTO analitica.dim_predio
                    (seudonimo_predio, zona, uso_predio, tipo_predio, base_imponible)
                VALUES ('etl-predio-sin-correspondencia', 'Z2', 'Comercio', 'URBANO', 960000.00)
                RETURNING id_predio
                """, Long.class);
        insertarHecho(idLote, seudonimo, idPredio, "IMPUESTO_PREDIAL", 2026, 1, LocalDate.of(2026, 3, 31),
                new BigDecimal("1010.88"), new BigDecimal("1010.88"));

        mvc.perform(get("/api/reportes/MOROSOS").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ETL_SIN_CORRESPONDENCIA"));
    }

    private Long insertarCargaEtl() {
        return jdbc.queryForObject("""
                INSERT INTO analitica.lote_etl
                    (fuente, fecha_corte, tipo_ejecucion, estado, fecha_inicio, fecha_fin,
                     registros_leidos, registros_aceptados, registros_rechazados)
                VALUES ('ps_etl_cobranza', DATE '2026-10-19', 'A_DEMANDA', 'PUBLICADO',
                        TIMESTAMP '2026-10-19 03:00', TIMESTAMP '2026-10-19 03:10', 2, 2, 0)
                RETURNING id_lote
                """, Long.class);
    }

    private String seudonimoContribuyente(String codigo) {
        return jdbc.queryForObject("""
                SELECT seudonimo_contribuyente FROM analitica.dim_contribuyente
                WHERE seudonimo_contribuyente = encode(hmac(?, 'samnet-demo-contribuyente', 'sha256'), 'hex')
                """, String.class, codigo);
    }

    private Long predio(String codigoPredio) {
        return jdbc.queryForObject("""
                SELECT id_predio FROM analitica.dim_predio
                WHERE seudonimo_predio = encode(hmac(?, 'samnet-demo-predio', 'sha256'), 'hex')
                """, Long.class, codigoPredio);
    }

    private void insertarHecho(Long idLote, String seudonimo, Long idPredio, String tributo, int anio, int cuota,
            LocalDate vencimiento, BigDecimal emitido, BigDecimal insoluto) {
        jdbc.update("""
                INSERT INTO analitica.hecho_cobranza
                    (id_lote, seudonimo_contribuyente, id_predio, tributo, anio, cuota, fecha_vencimiento,
                     monto_emitido, monto_insoluto, monto_pagado, monto_descuento, estado_pago)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'VENCIDA')
                """, idLote, seudonimo, idPredio, tributo, anio, cuota, vencimiento,
                emitido, insoluto, emitido.subtract(insoluto));
    }

    private MockHttpSession sesion(NombreRol rol) {
        String nombre = "usuario_" + rol.name().toLowerCase();
        Usuario usuario = usuarios.save(new Usuario(nombre, "Usuario " + rol, nombre + "@example.org",
                claves.hashear("clave-segura"), roles.findByNombre(rol).orElseThrow(), LocalDateTime.now()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        return sesion;
    }
}
