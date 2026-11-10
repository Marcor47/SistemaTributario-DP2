package pe.municipalidad.tributario.etl;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.SesionInterna;
import pe.municipalidad.tributario.seguridad.Usuario;

/*
 * Pruebas unitarias directas sin agentes dinámicos para HU040 (RF-053).
 * Valida todos los criterios de aceptación de forma inmediata y robusta.
 */
class AuditoriaHomologacionTest {

    @Test
    void testCriterio1_DecisionAutomaticaDetalleCompleto() {
        Long idMaestro = 100L;
        EntidadMaestra maestro = new EntidadMaestra(TipoEntidad.CONTRIBUYENTE, "hash-seudonimo-100", EstadoMaestro.ACTIVA);
        ReflectionTestUtils.setField(maestro, "idMaestro", idMaestro);

        LocalDateTime fecha = LocalDateTime.of(2026, 10, 19, 10, 0);
        DecisionHomologacion decision = DecisionHomologacion.automatica(
                maestro,
                NivelHomologacion.N1_EXACTA_NORMALIZADA,
                new BigDecimal("1.0000"),
                "{\"documento\":\"41028765\"}",
                fecha);
        ReflectionTestUtils.setField(decision, "idDecision", 501L);

        // Repositorio de decisiones simulado con Proxy estándar de Java
        DecisionHomologacionRepository repoDecision = (DecisionHomologacionRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DecisionHomologacionRepository.class},
                (proxy, method, args) -> {
                    if ("findByEntidadMaestraIdMaestroOrderByFechaHoraAsc".equals(method.getName())) {
                        return List.of(decision);
                    }
                    if ("findById".equals(method.getName())) {
                        return Optional.of(decision);
                    }
                    return null;
                });

        EntidadMaestraRepository repoMaestro = (EntidadMaestraRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{EntidadMaestraRepository.class},
                (proxy, method, args) -> {
                    if ("existsById".equals(method.getName())) {
                        return Boolean.TRUE;
                    }
                    if ("findBySeudonimo".equals(method.getName())) {
                        return Optional.of(maestro);
                    }
                    return null;
                });

        AuditoriaHomologacionService servicio = new AuditoriaHomologacionService(repoDecision, repoMaestro);

        // 1. Consultar por ID Maestro
        List<DecisionHomologacionRespuesta> respuestas = servicio.listarPorMaestro(idMaestro);
        assertEquals(1, respuestas.size());
        DecisionHomologacionRespuesta r = respuestas.get(0);

        assertEquals(501L, r.idDecision());
        assertEquals(idMaestro, r.idMaestro());
        assertEquals(TipoEntidad.CONTRIBUYENTE, r.tipoEntidad());
        assertEquals("hash-seudonimo-100", r.seudonimoMaestro());
        assertEquals(NivelHomologacion.N1_EXACTA_NORMALIZADA, r.nivelResolutor());
        assertEquals(new BigDecimal("1.0000"), r.puntaje());
        assertEquals("{\"documento\":\"41028765\"}", r.atributosCoincidentes());
        assertEquals(TipoDecision.AUTOMATICA, r.tipoDecision());
        assertNull(r.idUsuario());
        assertNull(r.motivo());
        assertEquals(fecha, r.fechaHora());

        // 2. Consultar por seudónimo
        List<DecisionHomologacionRespuesta> porSeudonimo = servicio.listarPorSeudonimo("hash-seudonimo-100");
        assertEquals(1, porSeudonimo.size());
        assertEquals(501L, porSeudonimo.get(0).idDecision());

        // 3. Consultar por ID de decisión
        DecisionHomologacionRespuesta porId = servicio.obtenerDecision(501L);
        assertEquals(501L, porId.idDecision());
    }

    @Test
    void testCriterio1_DecisionHumanaConUsuarioFechaYMotivo() {
        Long idMaestro = 200L;
        EntidadMaestra maestro = new EntidadMaestra(TipoEntidad.PREDIO, "hash-predio-200", EstadoMaestro.ACTIVA);
        ReflectionTestUtils.setField(maestro, "idMaestro", idMaestro);

        Usuario usuario = new Usuario("rdatos", "Juan Perez (Resp. Datos)", "rdatos@sm.gob.pe", "hash", null, LocalDateTime.now());
        ReflectionTestUtils.setField(usuario, "idUsuario", 77L);

        LoteETL lote = new LoteETL("ps_etl_predios", java.time.LocalDate.of(2026, 10, 19), TipoEjecucion.A_DEMANDA, LocalDateTime.now());
        CasoHomologacion caso = new CasoHomologacion(
                lote, maestro, NivelHomologacion.N2_SIMILITUD, new BigDecimal("0.8850"), "{\"dir\":\"Av. Marina 200\"}");
        ReflectionTestUtils.setField(caso, "idCaso", 88L);

        LocalDateTime ahora = LocalDateTime.of(2026, 10, 19, 15, 30);
        DecisionHomologacion decisionHumana = caso.aprobar(usuario, "Aprobado tras cotejo físico de catastro", ahora);
        ReflectionTestUtils.setField(decisionHumana, "idDecision", 602L);

        DecisionHomologacionRespuesta resp = DecisionHomologacionRespuesta.desde(decisionHumana);

        assertEquals(602L, resp.idDecision());
        assertEquals(idMaestro, resp.idMaestro());
        assertEquals(TipoEntidad.PREDIO, resp.tipoEntidad());
        assertEquals(88L, resp.idCaso());
        assertEquals(NivelHomologacion.N2_SIMILITUD, resp.nivelResolutor());
        assertEquals(new BigDecimal("0.8850"), resp.puntaje());
        assertEquals(TipoDecision.HUMANA, resp.tipoDecision());
        assertEquals(77L, resp.idUsuario());
        assertEquals("rdatos", resp.nombreUsuario());
        assertEquals("Juan Perez (Resp. Datos)", resp.nombreCompletoUsuario());
        assertEquals("Aprobado tras cotejo físico de catastro", resp.motivo());
        assertEquals(ahora, resp.fechaHora());
    }

    @Test
    void testValidacionEntidadMaestraInexistenteLanza404() {
        EntidadMaestraRepository repoMaestro = (EntidadMaestraRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{EntidadMaestraRepository.class},
                (proxy, method, args) -> {
                    if ("existsById".equals(method.getName())) return Boolean.FALSE;
                    if ("findBySeudonimo".equals(method.getName())) return Optional.empty();
                    return null;
                });

        AuditoriaHomologacionService servicio = new AuditoriaHomologacionService(null, repoMaestro);

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.listarPorMaestro(999L));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.listarPorSeudonimo("desconocido"));
    }

    @Test
    void testCriterio2_InalterabilidadYAusenciaDeEndpointsDeModificacion() {
        // Verificar que AuditoriaHomologacionController no declare métodos POST, PUT o DELETE
        Class<?> controllerClass = AuditoriaHomologacionController.class;
        for (var metodo : controllerClass.getDeclaredMethods()) {
            assertFalse(metodo.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class),
                    "El controlador NO debe tener endpoints POST");
            assertFalse(metodo.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class),
                    "El controlador NO debe tener endpoints PUT");
            assertFalse(metodo.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class),
                    "El controlador NO debe tener endpoints DELETE");
            assertFalse(metodo.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class),
                    "El controlador NO debe tener endpoints PATCH");
        }

        // Verificar que DecisionHomologacion tenga la anotación @Immutable
        assertTrue(DecisionHomologacion.class.isAnnotationPresent(org.hibernate.annotations.Immutable.class),
                "La entidad DecisionHomologacion debe estar marcada con @Immutable");
    }
}
