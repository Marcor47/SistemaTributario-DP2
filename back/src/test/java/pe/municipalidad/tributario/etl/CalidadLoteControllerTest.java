package pe.municipalidad.tributario.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * HU026: aplicar reglas de calidad en la carga del ETL (RF-038, RF-041).
 * Criterios de aceptación:
 * 1. Dado un registro que no cumple una regla de calidad (identidad/código nulo, DNI/RUC inválido,
 *    predio sin contribuyente, clave repetida o fecha de corte fuera de rango), cuando se procesa
 *    el lote, entonces se rechaza y no pasa a las tablas maestras.
 * 2. Dado un lote rechazado, cuando ocurre, entonces no afecta los datos ya publicados.
 */
@PruebaIntegracion
@Transactional
class CalidadLoteControllerTest {

    private static final String RUTA_REGLAS = "/api/etl/calidad/reglas";
    private static final String RUTA_EVALUACIONES = "/api/etl/calidad/evaluaciones";
    private static final String RUTA_LOTES = "/api/etl/calidad/lotes";
    private static final LocalDate FECHA_CORTE = LocalDate.of(2026, 10, 19);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoteETLRepository lotes;

    @Autowired
    private ReglaCalidadRepository reglas;

    @Test
    void listarReglasDevuelveCatalogoDeReglasCalidad() throws Exception {
        mockMvc.perform(get(RUTA_REGLAS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[?(@.codigo == 'R1')].descripcion").isNotEmpty())
                .andExpect(jsonPath("$[?(@.codigo == 'R2')].descripcion").isNotEmpty())
                .andExpect(jsonPath("$[?(@.codigo == 'R3')].descripcion").isNotEmpty())
                .andExpect(jsonPath("$[?(@.codigo == 'R4')].descripcion").isNotEmpty())
                .andExpect(jsonPath("$[?(@.codigo == 'R5')].descripcion").isNotEmpty());
    }

    @Test
    void loteConRegistrosValidosPasaCalidadYSePublica() throws Exception {
        LoteETL lote = nuevoLote();

        String json = """
                {
                    "idLote": %d,
                    "registros": [
                        {
                            "codigo": "014502",
                            "tipo_documento": "DNI",
                            "numero_documento": "41028765",
                            "nombre": "MEDINA RAMIREZ, CARLOS ALBERTO",
                            "fecha_corte": "2026-10-19"
                        },
                        {
                            "codigo_predio": "P-1001",
                            "contribuyente_codigo": "014502",
                            "zona": "Z01",
                            "condicion_propiedad": "PROPIETARIO",
                            "fecha_corte": "2026-10-19"
                        }
                    ]
                }
                """.formatted(lote.getIdLote());

        mockMvc.perform(post(RUTA_EVALUACIONES).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLote").value(lote.getIdLote()))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.lotePublicado").value(true))
                .andExpect(jsonPath("$.registrosLeidos").value(2))
                .andExpect(jsonPath("$.registrosAceptados").value(2))
                .andExpect(jsonPath("$.registrosRechazados").value(0))
                .andExpect(jsonPath("$.resultadosReglas", hasSize(5)))
                .andExpect(jsonPath("$.resultadosReglas[?(@.resultado != 'CUMPLE')]").isEmpty());

        LoteETL guardado = lotes.findById(lote.getIdLote()).orElseThrow();
        assertThat(guardado.getEstado()).isEqualTo(EstadoLote.PUBLICADO);
        assertThat(guardado.getRegistrosAceptados()).isEqualTo(2);
        assertThat(guardado.getRegistrosRechazados()).isEqualTo(0);
    }

    @Test
    void loteConInfraccionesDeCalidadSeRechazaYNoPasaATablasMaestras() throws Exception {
        LoteETL lote = nuevoLote();

        // Incumple R1 (código nulo), R2 (DNI corto), R3 (predio sin contribuyente sin marcar), R4 (claves repetidas), R5 (corte fuera de rango)
        String json = """
                {
                    "idLote": %d,
                    "registros": [
                        {
                            "tipo_documento": "DNI",
                            "numero_documento": "123",
                            "fecha_corte": "2026-10-19"
                        },
                        {
                            "codigo_predio": "P-9999",
                            "zona": "Z01",
                            "fecha_corte": "2026-10-19"
                        },
                        {
                            "codigo": "DUP-001",
                            "tipo_documento": "DNI",
                            "numero_documento": "88888888",
                            "fecha_corte": "2026-10-19"
                        },
                        {
                            "codigo": "DUP-001",
                            "tipo_documento": "DNI",
                            "numero_documento": "88888888",
                            "fecha_corte": "2026-10-19"
                        },
                        {
                            "codigo": "CORTE-ERR",
                            "tipo_documento": "DNI",
                            "numero_documento": "77777777",
                            "fecha_corte": "2025-01-01"
                        }
                    ]
                }
                """.formatted(lote.getIdLote());

        mockMvc.perform(post(RUTA_EVALUACIONES).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLote").value(lote.getIdLote()))
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.lotePublicado").value(false))
                .andExpect(jsonPath("$.registrosLeidos").value(5))
                .andExpect(jsonPath("$.registrosRechazados").isNumber())
                .andExpect(jsonPath("$.resultadosReglas[?(@.resultado == 'FALLA')]").isNotEmpty());

        LoteETL guardado = lotes.findById(lote.getIdLote()).orElseThrow();
        assertThat(guardado.getEstado()).isEqualTo(EstadoLote.RECHAZADO);
        assertThat(guardado.getRegistrosRechazados()).isGreaterThan(0);
    }

    @Test
    void unLoteRechazadoNoAfectaLosDatosDeUnLotePreviamentePublicado() throws Exception {
        // Lote publicado previo
        LoteETL lotePrevio = lotes.saveAndFlush(new LoteETL("ps_etl_cobranza", FECHA_CORTE.minusDays(1),
                TipoEjecucion.PROGRAMADA, FECHA_CORTE.minusDays(1).atTime(2, 0)));
        lotePrevio.publicar(100, 100, FECHA_CORTE.minusDays(1).atTime(2, 5));
        lotes.saveAndFlush(lotePrevio);

        // Nuevo lote con errores de calidad
        LoteETL nuevoLote = nuevoLote();
        String json = """
                {
                    "idLote": %d,
                    "registros": [
                        {
                            "codigo": "014502",
                            "tipo_documento": "DNI",
                            "numero_documento": "123"
                        }
                    ]
                }
                """.formatted(nuevoLote.getIdLote());

        mockMvc.perform(post(RUTA_EVALUACIONES).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"));

        // El lote previo publicado permanece intacto
        LoteETL lotePrevioConsultado = lotes.findById(lotePrevio.getIdLote()).orElseThrow();
        assertThat(lotePrevioConsultado.getEstado()).isEqualTo(EstadoLote.PUBLICADO);
        assertThat(lotePrevioConsultado.getRegistrosAceptados()).isEqualTo(100);
    }

    @Test
    void rechazaEvaluacionDeLoteInexistenteOYaCerrado() throws Exception {
        // Inexistente -> 404
        mockMvc.perform(post(RUTA_EVALUACIONES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLote\": 999999}"))
                .andExpect(status().isNotFound());

        // Ya procesado/publicado -> 422 (ReglaNegocioException)
        LoteETL lotePublicado = nuevoLote();
        lotePublicado.publicar(10, 10, LocalDateTime.now());
        lotes.saveAndFlush(lotePublicado);

        mockMvc.perform(post(RUTA_EVALUACIONES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLote\": " + lotePublicado.getIdLote() + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LOTE_YA_PROCESADO"));
    }

    @Test
    void consultarCalidadDeLoteDevuelveResultadosRegistrados() throws Exception {
        LoteETL lote = nuevoLote();
        String json = """
                {
                    "idLote": %d,
                    "registros": [
                        {
                            "codigo": "014502",
                            "tipo_documento": "DNI",
                            "numero_documento": "41028765"
                        }
                    ]
                }
                """.formatted(lote.getIdLote());

        mockMvc.perform(post(RUTA_EVALUACIONES).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        // Consultar por GET
        mockMvc.perform(get(RUTA_LOTES + "/" + lote.getIdLote()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLote").value(lote.getIdLote()))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.resultadosReglas", hasSize(5)));
    }

    private LoteETL nuevoLote() {
        return lotes.saveAndFlush(new LoteETL("ps_etl_contribuyentes", FECHA_CORTE,
                TipoEjecucion.A_DEMANDA, FECHA_CORTE.atTime(2, 0)));
    }
}
