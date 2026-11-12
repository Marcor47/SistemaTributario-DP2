package pe.municipalidad.tributario.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
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
 * ETL por capas (SAMNET → bronce → plata → oro) y bandeja de homologación (RF-038 a RF-041, RF-053).
 * La carga de prueba (R__zz_carga_etl_prueba) ya ejecutó el ETL con corte 2026-10-19 sobre los datos de prueba,
 * que incluyen los duplicados de R__datos_prueba_09_duplicados_homologacion. Cada test deshace lo que cambia.
 */
@PruebaIntegracion
@Transactional
@TestPropertySource(properties = {
        "tributario.etl.clave-seudonimo-contribuyente=samnet-demo-contribuyente",
        "tributario.etl.clave-seudonimo-predio=samnet-demo-predio" })
class EtlPipelineTest {

    private static final LocalDate CORTE = LocalDate.of(2026, 10, 19);

    /*
     * Cómo armaba los hechos la carga anterior (R__datos_prueba_10 hasta noviembre de 2026), escribiendo el modelo
     * estrella a mano desde las tablas de SAMNET. El ETL debe publicar exactamente lo mismo.
     */
    private static final String HECHOS_CARGA_ANTERIOR = """
            WITH predio_tecnico AS (
                SELECT DISTINCT ON (t.contribuyente_codigo) t.contribuyente_codigo, t.codigo_predio
                FROM samnet.predio_titular t
                ORDER BY t.contribuyente_codigo, t.codigo_predio
            ),
            cuotas AS (
                SELECT o.*, COALESCE(o.codigo_predio, pt.codigo_predio) AS predio_fact,
                       o.monto_emitido - o.monto_pagado - o.monto_descontado AS saldo
                FROM samnet.obligacion o
                LEFT JOIN predio_tecnico pt ON pt.contribuyente_codigo = o.contribuyente_codigo
                WHERE o.anio <= 2026
            )
            SELECT encode(hmac(c.contribuyente_codigo, 'samnet-demo-contribuyente', 'sha256'), 'hex') AS contribuyente,
                   encode(hmac(c.predio_fact, 'samnet-demo-predio', 'sha256'), 'hex') AS predio,
                   c.tributo, c.anio, c.numero_cuota AS cuota, c.fecha_vencimiento, c.monto_emitido,
                   c.saldo AS insoluto, c.monto_pagado, c.monto_descontado AS descuento,
                   CASE WHEN c.saldo <= 0 THEN 'PAGADA'
                        WHEN c.fecha_vencimiento < DATE '2026-10-19' THEN 'VENCIDA' ELSE 'PENDIENTE' END AS estado
            FROM cuotas c
            WHERE c.predio_fact IS NOT NULL
            """;

    private static final String HECHOS_DEL_LOTE = """
            SELECT h.seudonimo_contribuyente, p.seudonimo_predio, h.tributo, h.anio, h.cuota, h.fecha_vencimiento,
                   h.monto_emitido, h.monto_insoluto, h.monto_pagado, h.monto_descuento, h.estado_pago
            FROM analitica.hecho_cobranza h
            JOIN analitica.dim_predio p ON p.id_predio = h.id_predio
            WHERE h.id_lote = ?
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EtlService etl;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private RolRepository roles;
    @Autowired
    private ClaveInternaService claves;

    @Test
    void laCargaDePruebaPasaPorBronceYPlataYPublicaLoMismoQueLaCargaAnterior() {
        Long lote = loteEtlDePrueba();

        assertThat(jdbc.queryForList("SELECT procedimiento FROM bronce.extraccion WHERE id_lote = ?", String.class,
                lote)).containsExactlyInAnyOrder("ps_etl_contribuyentes", "ps_etl_predios", "ps_etl_cobranza",
                "ps_etl_pagos");
        assertThat(cuenta("SELECT count(*) FROM bronce.cobranza WHERE id_lote = ?", lote))
                .isEqualTo(cuenta("SELECT count(*) FROM samnet.ps_etl_cobranza(DATE '2026-10-19')"));
        assertThat(jdbc.queryForObject("SELECT estado FROM analitica.lote_etl WHERE id_lote = ?", String.class, lote))
                .isEqualTo("PUBLICADO");

        // Mismos hechos, montos, estados y seudónimos que la carga anterior, fila por fila.
        assertThat(cuenta("SELECT count(*) FROM analitica.hecho_cobranza WHERE id_lote = ?", lote)).isEqualTo(1218);
        assertThat(cuenta("SELECT count(*) FROM ((" + HECHOS_CARGA_ANTERIOR + ") EXCEPT ALL (" + HECHOS_DEL_LOTE
                + ")) d", lote)).isZero();
        assertThat(cuenta("SELECT count(*) FROM ((" + HECHOS_DEL_LOTE + ") EXCEPT ALL (" + HECHOS_CARGA_ANTERIOR
                + ")) d", lote)).isZero();

        // Nuevo: los pagos también pasan al oro, y la fecha y el canal del último pago de cada cuota.
        assertThat(cuenta("SELECT count(*) FROM analitica.hecho_pago WHERE id_lote = ?", lote))
                .isEqualTo(cuenta("SELECT count(*) FROM samnet.ps_etl_pagos(DATE '2026-10-19')"));
        assertThat(cuenta("SELECT count(*) FROM analitica.hecho_cobranza WHERE id_lote = ? AND canal_pago IS NOT NULL",
                lote)).isPositive();
    }

    @Test
    void lasReglasDejanEnCuarentenaLoQueFallaYMarcanElPredioSinTitular() {
        Long lote = loteEtlDePrueba();

        assertThat(jdbc.queryForList("""
                SELECT rc.codigo || ':' || r.resultado || ':' || r.registros_con_error
                FROM analitica.resultado_regla r JOIN analitica.regla_calidad rc ON rc.id_regla = r.id_regla
                WHERE r.id_lote = ? ORDER BY rc.codigo
                """, String.class, lote))
                .containsExactly("R1:CUMPLE:0", "R2:OBSERVA:1", "R3:OBSERVA:0", "R4:CUMPLE:0", "R5:CUMPLE:0");
        assertThat(jdbc.queryForList("SELECT clave_origen || ':' || regla FROM plata.cuarentena WHERE id_lote = ?",
                String.class, lote)).containsExactly("990001:R2");
        assertThat(cuenta("SELECT count(*) FROM plata.contribuyente WHERE codigo = '990001'")).isZero();

        // R3: el predio sin titular no se pierde: se publica marcado para revisión.
        assertThat(jdbc.queryForObject("""
                SELECT e.estado FROM plata.predio p JOIN maestros.entidad_maestra e ON e.id_maestro = p.id_maestro
                WHERE p.codigo_predio = '099001-01' AND p.marcado_revision
                """, String.class)).isEqualTo("PENDIENTE_REVISION");
        assertThat(jdbc.queryForObject("SELECT registros_rechazados FROM analitica.lote_etl WHERE id_lote = ?",
                Integer.class, lote)).isEqualTo(1);
    }

    @Test
    void laHomologacionUneSoloLoExactoYDejaLoParecidoParaElResponsable() throws Exception {
        // N1: el RUC 10 lleva el DNI de la misma persona; el predio repite el código catastral.
        assertThat(maestroDe("903218")).isEqualTo(maestroDe("033218"));
        assertThat(jdbc.queryForObject("SELECT nivel_homologacion FROM plata.contribuyente WHERE codigo = '903218'",
                String.class)).isEqualTo("N1_EXACTA_NORMALIZADA");
        assertThat(maestroDe("914502-01")).isEqualTo(maestroDe("014502-01"));

        // N2: tres casos pendientes, cada uno con su entidad provisional. Ninguno entre los contribuyentes originales.
        assertThat(jdbc.queryForList("""
                SELECT co.clave_origen FROM analitica.caso_homologacion c
                JOIN maestros.correspondencia_origen co ON co.id_maestro = c.id_maestro_provisional
                WHERE c.estado = 'PENDIENTE' ORDER BY 1
                """, String.class)).containsExactly("914502", "921390", "933219");

        // La bandeja no expone datos personales; solo puntajes.
        mvc.perform(get("/api/homologacion/casos").session(sesion(NombreRol.RESPONSABLE_DATOS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].nivelAlcanzado").value("N2_SIMILITUD"))
                .andExpect(jsonPath("$[0].atributosComparados.nombre.similitud").value(1.0))
                .andExpect(jsonPath("$[0].provisional.estado").value("PENDIENTE_REVISION"))
                .andExpect(content().string(not(containsString("41028765"))))
                .andExpect(content().string(not(containsString("GARC"))));
    }

    @Test
    void elDetalleMuestraAmbosRegistrosYQuedaAuditado() throws Exception {
        Long caso = casoDe("914502");
        long auditoriasAntes = cuenta("SELECT count(*) FROM seguridad.registro_auditoria WHERE accion = 'REIDENTIFICAR'");

        mvc.perform(get("/api/homologacion/casos/{id}", caso).session(sesion(NombreRol.RESPONSABLE_DATOS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidato.registros[0].codigo").value("014502"))
                .andExpect(jsonPath("$.candidato.registros[0].numeroDocumento").value("41028765"))
                .andExpect(jsonPath("$.provisional.registros[0].codigo").value("914502"))
                .andExpect(jsonPath("$.provisional.registros[0].numeroDocumento").value("41208765"));

        assertThat(cuenta("SELECT count(*) FROM seguridad.registro_auditoria WHERE accion = 'REIDENTIFICAR'"))
                .isEqualTo(auditoriasAntes + 1);
    }

    @Test
    void aprobarUneElDuplicadoYElSiguienteLotePublicaSusCuotasConElSeudonimoDelOriginal() throws Exception {
        Long caso = casoDe("914502");
        Long original = maestroDe("014502");
        Long provisional = maestroDe("914502");
        // Una cuota que SAMNET registró a nombre del duplicado, en su predio duplicado.
        jdbc.update("""
                INSERT INTO samnet.obligacion (contribuyente_codigo, codigo_predio, tributo, anio, numero_cuota,
                        fecha_vencimiento, monto_emitido)
                VALUES ('914502', '914502-01', 'ARBITRIOS', 2026, 1, DATE '2026-02-27', 50.00)
                """);

        mvc.perform(post("/api/homologacion/casos/{id}/aprobacion", caso).with(csrf())
                        .session(sesion(NombreRol.RESPONSABLE_DATOS))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"DNI con dos cifras invertidas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.idMaestroResultante").value(original))
                .andExpect(jsonPath("$.correspondenciasMovidas").value(1));

        assertThat(maestroDe("914502")).isEqualTo(original);
        assertThat(estadoMaestro(provisional)).isEqualTo("FUSIONADA");
        assertThat(jdbc.queryForObject("""
                SELECT tipo_decision || ':' || motivo FROM analitica.decision_homologacion WHERE id_caso = ?
                """, String.class, caso)).isEqualTo("HUMANA:DNI con dos cifras invertidas");

        // Siguiente lote: el duplicado entra por correspondencia previa, sin caso nuevo, con el seudónimo del original.
        EvaluacionCalidadRespuesta lote = etl.ejecutar(CORTE, TipoEjecucion.A_DEMANDA);
        assertThat(lote.lotePublicado()).isTrue();
        assertThat(jdbc.queryForObject("SELECT nivel_homologacion FROM plata.contribuyente WHERE codigo = '914502'",
                String.class)).isEqualTo("N0_CORRESPONDENCIA_PREVIA");
        assertThat(cuenta("SELECT count(*) FROM analitica.caso_homologacion WHERE id_lote = ?", lote.idLote())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT h.seudonimo_contribuyente || '|' || p.seudonimo_predio
                FROM analitica.hecho_cobranza h JOIN analitica.dim_predio p ON p.id_predio = h.id_predio
                WHERE h.id_lote = ? AND h.monto_emitido = 50.00 AND h.anio = 2026 AND h.cuota = 1
                """, String.class, lote.idLote()))
                .isEqualTo(seudonimo("014502", "samnet-demo-contribuyente") + "|"
                        + seudonimo("014502-01", "samnet-demo-predio"));
        assertThat(cuenta("SELECT count(*) FROM analitica.hecho_cobranza WHERE id_lote = ?", lote.idLote()))
                .isEqualTo(1219);
    }

    @Test
    void rechazarDejaElRegistroComoEntidadPropiaYNoVuelveALaBandeja() throws Exception {
        Long caso = casoDe("933219");
        Long provisional = maestroDe("933219");

        mvc.perform(post("/api/homologacion/casos/{id}/rechazo", caso).with(csrf())
                        .session(sesion(NombreRol.RESPONSABLE_DATOS))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Homónimo: es el hijo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.idMaestroResultante").value(provisional));
        assertThat(estadoMaestro(provisional)).isEqualTo("ACTIVA");

        EvaluacionCalidadRespuesta lote = etl.ejecutar(CORTE, TipoEjecucion.A_DEMANDA);
        assertThat(maestroDe("933219")).isEqualTo(provisional).isNotEqualTo(maestroDe("033218"));
        assertThat(cuenta("SELECT count(*) FROM analitica.caso_homologacion WHERE id_lote = ?", lote.idLote())).isZero();

        // Un caso resuelto no se vuelve a resolver.
        mvc.perform(post("/api/homologacion/casos/{id}/aprobacion", caso).with(csrf())
                        .session(sesion(NombreRol.RESPONSABLE_DATOS))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"otra vez\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CASO_RESUELTO"));
    }

    @Test
    void reasignarUneConOtraEntidadYValidaElDestino() throws Exception {
        Long caso = casoDe("921390");
        Long empresa = maestroDe("021390");
        MockHttpSession responsable = sesion(NombreRol.RESPONSABLE_DATOS);

        mvc.perform(post("/api/homologacion/casos/{id}/reasignacion", caso).with(csrf()).session(responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idMaestro\":" + maestroDe("921390") + ",\"motivo\":\"x\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("DESTINO_INVALIDO"));
        mvc.perform(post("/api/homologacion/casos/{id}/reasignacion", caso).with(csrf()).session(responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idMaestro\":" + maestroDe("021390-01") + ",\"motivo\":\"x\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/homologacion/casos/{id}/reasignacion", caso).with(csrf()).session(responsable)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idMaestro\":" + empresa + "}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/homologacion/casos/{id}/reasignacion", caso).with(csrf()).session(responsable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idMaestro\":" + empresa + ",\"motivo\":\"Misma razón social y domicilio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REASIGNADO"));
        assertThat(maestroDe("921390")).isEqualTo(empresa);

        // HU040: la decisión humana aparece en la auditoría de la entidad.
        mvc.perform(get("/api/homologacion/maestros/{id}/decisiones", empresa).session(responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tipoDecision", hasItem("HUMANA")));
    }

    @Test
    void soloElResponsableDeDatosOElAdministradorUsanLaBandeja() throws Exception {
        mvc.perform(get("/api/homologacion/casos").session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/homologacion/casos").param("estado", "TODOS").session(sesion(NombreRol.ADMINISTRADOR)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/homologacion/casos").param("estado", "OTRO").session(sesion(NombreRol.ADMINISTRADOR)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elResponsableEjecutaElEtlADemandaYRevisaLaCuarentena() throws Exception {
        MockHttpSession responsable = sesion(NombreRol.RESPONSABLE_DATOS);
        String cuerpo = mvc.perform(post("/api/etl/lotes").with(csrf()).session(responsable)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fechaCorte\":\"2026-10-19\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"))
                .andExpect(jsonPath("$.tipoEjecucion").value("A_DEMANDA"))
                .andExpect(jsonPath("$.resultadosReglas", hasSize(5)))
                .andReturn().getResponse().getContentAsString();
        Long idLote = Long.valueOf(cuerpo.replaceAll(".*\"idLote\":(\\d+).*", "$1"));

        mvc.perform(get("/api/etl/lotes/{id}/cuarentena", idLote).session(responsable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].claveOrigen").value("990001"))
                .andExpect(jsonPath("$[0].regla").value("R2"));

        // El nuevo lote es el que leen los reportes, con los mismos hechos.
        assertThat(cuenta("SELECT count(*) FROM analitica.hecho_cobranza WHERE id_lote = ?", idLote)).isEqualTo(1218);
        assertThat(cuenta("SELECT count(*) FROM ((" + HECHOS_CARGA_ANTERIOR + ") EXCEPT ALL (" + HECHOS_DEL_LOTE
                + ")) d", idLote)).isZero();

        mvc.perform(post("/api/etl/lotes").with(csrf()).session(responsable)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fechaCorte\":\"2026-10-20\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FECHA_CORTE_FUTURA"));
        mvc.perform(post("/api/etl/lotes").with(csrf()).session(sesion(NombreRol.GERENCIA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unLoteConDemasiadosErroresSeRechazaSinTocarLoPublicado() {
        Long publicado = loteEtlDePrueba();
        // Más del 10 % de los contribuyentes con un DNI inválido.
        jdbc.update("UPDATE samnet.contribuyente SET numero_documento = '123' WHERE codigo = '014502'");
        jdbc.update("UPDATE samnet.contribuyente SET numero_documento = '124' WHERE codigo = '033218'");

        EvaluacionCalidadRespuesta lote = etl.ejecutar(CORTE, TipoEjecucion.A_DEMANDA);

        assertThat(lote.estado()).isEqualTo(EstadoLote.RECHAZADO);
        assertThat(lote.resultadosReglas()).anySatisfy(r -> {
            assertThat(r.codigoRegla()).isEqualTo("R2");
            assertThat(r.resultado()).isEqualTo(ResultadoCalidad.FALLA);
        });
        assertThat(cuenta("SELECT count(*) FROM analitica.hecho_cobranza WHERE id_lote = ?", lote.idLote())).isZero();
        assertThat(cuenta("SELECT count(*) FROM plata.contribuyente WHERE id_lote = ?", publicado)).isPositive();
        assertThat(cuenta("SELECT count(*) FROM plata.cuarentena WHERE id_lote = ?", lote.idLote())).isPositive();
    }

    private Long loteEtlDePrueba() {
        return jdbc.queryForObject("""
                SELECT max(l.id_lote) FROM analitica.lote_etl l
                WHERE l.fuente = 'ps_etl_cobranza' AND l.fecha_corte = DATE '2026-10-19' AND l.estado = 'PUBLICADO'
                  AND EXISTS (SELECT 1 FROM bronce.extraccion e WHERE e.id_lote = l.id_lote)
                """, Long.class);
    }

    private Long maestroDe(String claveOrigen) {
        return jdbc.queryForObject(
                "SELECT id_maestro FROM maestros.correspondencia_origen WHERE sistema_origen = 'SAMNET' AND clave_origen = ?",
                Long.class, claveOrigen);
    }

    private Long casoDe(String claveOrigen) {
        return jdbc.queryForObject("""
                SELECT c.id_caso FROM analitica.caso_homologacion c
                JOIN maestros.correspondencia_origen co ON co.id_maestro = c.id_maestro_provisional
                WHERE co.clave_origen = ?
                """, Long.class, claveOrigen);
    }

    private String estadoMaestro(Long idMaestro) {
        return jdbc.queryForObject("SELECT estado FROM maestros.entidad_maestra WHERE id_maestro = ?", String.class,
                idMaestro);
    }

    private String seudonimo(String codigo, String clave) {
        return jdbc.queryForObject("SELECT encode(hmac(?, ?, 'sha256'), 'hex')", String.class, codigo, clave);
    }

    private long cuenta(String sql, Object... argumentos) {
        return jdbc.queryForObject(sql, Long.class, argumentos);
    }

    private MockHttpSession sesion(NombreRol rol) {
        String nombre = "etl_" + rol.name().toLowerCase();
        Usuario usuario = usuarios.findByNombreUsuarioIgnoreCase(nombre).orElseGet(() -> usuarios.save(new Usuario(
                nombre, "Usuario " + rol, nombre + "@example.org", claves.hashear("clave-segura"),
                roles.findByNombre(rol).orElseThrow(), LocalDateTime.now())));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionInterna.ATRIBUTO_USUARIO_INTERNO, usuario.getIdUsuario());
        return sesion;
    }
}
