package pe.municipalidad.tributario.etl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.ConflictoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.common.ReglaNegocioException;
import pe.municipalidad.tributario.seguridad.RegistroAuditoria;
import pe.municipalidad.tributario.seguridad.RegistroAuditoriaRepository;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/*
 * RF-040: bandeja de homologación con revisión humana. El ETL (etl.ejecutar_lote) deja aquí los registros que se
 * parecen a una entidad existente sin coincidir del todo (N2). Mientras tanto se publican con una entidad provisional.
 *
 *   aprobar    es la misma entidad que el candidato: las correspondencias de la provisional pasan al candidato
 *   rechazar   es otra entidad: la provisional queda activa con su propio seudónimo
 *   reasignar  es la misma que otra entidad (no el candidato): las correspondencias pasan a esa
 *
 * Cada resolución registra una DecisionHomologacion HUMANA inalterable (RF-053), que se consulta en HU040. Los
 * maestros y la plata cambian en el acto; el modelo estrella, desde el siguiente lote: los lotes publicados no se
 * reescriben.
 */
@Service
public class HomologacionCasoService {

    static final String EFECTO = "Maestros actualizados; el modelo estrella lo refleja desde el siguiente lote del ETL.";

    private final CasoHomologacionRepository casos;
    private final EntidadMaestraRepository maestros;
    private final DecisionHomologacionRepository decisiones;
    private final UsuarioRepository usuarios;
    private final RegistroAuditoriaRepository auditorias;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock reloj;

    public HomologacionCasoService(CasoHomologacionRepository casos, EntidadMaestraRepository maestros,
            DecisionHomologacionRepository decisiones, UsuarioRepository usuarios,
            RegistroAuditoriaRepository auditorias, JdbcTemplate jdbc, JsonMapper json, Clock reloj) {
        this.casos = casos;
        this.maestros = maestros;
        this.decisiones = decisiones;
        this.usuarios = usuarios;
        this.auditorias = auditorias;
        this.jdbc = jdbc;
        this.json = json;
        this.reloj = reloj;
    }

    /* estado nulo: todos los casos. */
    @Transactional(readOnly = true)
    public List<CasoHomologacionRespuesta> listar(EstadoCaso estado) {
        return casos.listar(estado).stream().map(this::respuesta).toList();
    }

    /* Muestra los registros de SAMNET de ambos lados en claro: se audita como reidentificación (RNF-018). */
    @Transactional
    public CasoHomologacionDetalleRespuesta detalle(Long idCaso, String nombreUsuario) {
        CasoHomologacion caso = casos.findById(idCaso).orElseThrow(() -> noExiste(idCaso));
        Usuario usuario = usuario(nombreUsuario);
        auditorias.save(new RegistroAuditoria(usuario, "REIDENTIFICAR", "CasoHomologacion", null,
                "caso:" + idCaso, LocalDateTime.now(reloj)));
        return new CasoHomologacionDetalleRespuesta(respuesta(caso), lado(caso.getMaestroCandidato()),
                lado(caso.getMaestroProvisional()));
    }

    @Transactional
    public ResolucionCasoRespuesta aprobar(Long idCaso, String motivo, String nombreUsuario) {
        CasoHomologacion caso = bloquear(idCaso);
        EntidadMaestra candidato = caso.getMaestroCandidato();
        if (candidato != null) {
            exigirDestinoValido(caso, candidato);
        }
        DecisionHomologacion decision = caso.aprobar(usuario(nombreUsuario), motivo, LocalDateTime.now(reloj));
        int movidas = unir(caso.getMaestroProvisional(), candidato);
        return resultado(caso, decisiones.saveAndFlush(decision), candidato, movidas);
    }

    @Transactional
    public ResolucionCasoRespuesta rechazar(Long idCaso, String motivo, String nombreUsuario) {
        CasoHomologacion caso = bloquear(idCaso);
        EntidadMaestra provisional = caso.getMaestroProvisional();
        if (provisional == null) {
            throw new ReglaNegocioException("SIN_ENTIDAD_PROVISIONAL",
                    "El caso no tiene una entidad provisional que activar; use la reasignación.");
        }
        DecisionHomologacion decision = caso.rechazar(provisional, usuario(nombreUsuario), motivo,
                LocalDateTime.now(reloj));
        provisional.activar();
        maestros.saveAndFlush(provisional);
        return resultado(caso, decisiones.saveAndFlush(decision), provisional, 0);
    }

    @Transactional
    public ResolucionCasoRespuesta reasignar(Long idCaso, Long idMaestro, String motivo, String nombreUsuario) {
        CasoHomologacion caso = bloquear(idCaso);
        EntidadMaestra destino = maestros.findById(idMaestro)
                .orElseThrow(() -> new RecursoNoEncontradoException("ENTIDAD_MAESTRA_NO_ENCONTRADA",
                        "No existe la entidad maestra con id " + idMaestro + "."));
        exigirDestinoValido(caso, destino);
        DecisionHomologacion decision = caso.reasignar(destino, usuario(nombreUsuario), motivo,
                LocalDateTime.now(reloj));
        int movidas = unir(caso.getMaestroProvisional(), destino);
        return resultado(caso, decisiones.saveAndFlush(decision), destino, movidas);
    }

    private CasoHomologacion bloquear(Long idCaso) {
        return casos.bloquear(idCaso).orElseThrow(() -> noExiste(idCaso));
    }

    private void exigirDestinoValido(CasoHomologacion caso, EntidadMaestra destino) {
        EntidadMaestra provisional = caso.getMaestroProvisional();
        if (provisional != null && provisional.getIdMaestro().equals(destino.getIdMaestro())) {
            throw new ReglaNegocioException("DESTINO_INVALIDO",
                    "La entidad de destino es la provisional del propio caso; para mantenerla, rechace el caso.");
        }
        if (destino.getEstado() == EstadoMaestro.FUSIONADA) {
            throw new ConflictoException("DESTINO_FUSIONADO",
                    "La entidad " + destino.getIdMaestro() + " ya se unió a otra; elija la entidad vigente.");
        }
        if (provisional != null && provisional.getTipoEntidad() != destino.getTipoEntidad()) {
            throw new ReglaNegocioException("DESTINO_INVALIDO",
                    "No se puede unir un " + provisional.getTipoEntidad() + " con un " + destino.getTipoEntidad() + ".");
        }
    }

    /*
     * Pasa las correspondencias (y la plata) de la entidad provisional a la de destino y marca la provisional como
     * fusionada. El siguiente lote encuentra esos códigos por correspondencia previa (N0) y los publica con el
     * seudónimo del destino.
     */
    private int unir(EntidadMaestra provisional, EntidadMaestra destino) {
        if (provisional == null || destino == null) {
            return 0;
        }
        int movidas = jdbc.update("UPDATE maestros.correspondencia_origen SET id_maestro = ? WHERE id_maestro = ?",
                destino.getIdMaestro(), provisional.getIdMaestro());
        String tabla = provisional.getTipoEntidad() == TipoEntidad.PREDIO ? "plata.predio" : "plata.contribuyente";
        jdbc.update("UPDATE " + tabla + " SET id_maestro = ? WHERE id_maestro = ?",
                destino.getIdMaestro(), provisional.getIdMaestro());
        provisional.fusionar();
        // El estado se escribe ya: el ETL y las consultas por JDBC de esta transacción leen la base, no la sesión JPA.
        maestros.saveAndFlush(provisional);
        return movidas;
    }

    private ResolucionCasoRespuesta resultado(CasoHomologacion caso, DecisionHomologacion decision,
            EntidadMaestra resultante, int movidas) {
        return new ResolucionCasoRespuesta(caso.getIdCaso(), caso.getEstado(), decision.getIdDecision(),
                resultante == null ? null : resultante.getIdMaestro(),
                resultante == null ? null : resultante.getSeudonimo(), movidas, EFECTO);
    }

    private CasoHomologacionRespuesta respuesta(CasoHomologacion caso) {
        JsonNode atributos;
        try {
            atributos = json.readTree(caso.getAtributosComparados());
        } catch (RuntimeException ex) {
            atributos = json.getNodeFactory().textNode(caso.getAtributosComparados());
        }
        return CasoHomologacionRespuesta.desde(caso, atributos);
    }

    private CasoHomologacionDetalleRespuesta.Lado lado(EntidadMaestra maestro) {
        if (maestro == null) {
            return null;
        }
        List<CasoHomologacionDetalleRespuesta.RegistroOrigen> registros = maestro.getTipoEntidad() == TipoEntidad.PREDIO
                ? jdbc.query("""
                        SELECT codigo_predio, numero_catastral, ubicacion, tipo_predio, nivel_homologacion,
                               marcado_revision
                        FROM plata.predio WHERE id_maestro = ? ORDER BY codigo_predio
                        """, (rs, i) -> new CasoHomologacionDetalleRespuesta.RegistroOrigen(
                                rs.getString("codigo_predio"), "CODIGO_CATASTRAL", rs.getString("numero_catastral"),
                                rs.getString("ubicacion"), rs.getString("ubicacion"), rs.getString("tipo_predio"),
                                rs.getBoolean("marcado_revision") ? "MARCADO_REVISION" : null,
                                rs.getString("nivel_homologacion")), maestro.getIdMaestro())
                : jdbc.query("""
                        SELECT codigo, tipo_documento, numero_documento, nombre, domicilio_fiscal, tipo_persona,
                               estado, nivel_homologacion
                        FROM plata.contribuyente WHERE id_maestro = ? ORDER BY codigo
                        """, (rs, i) -> new CasoHomologacionDetalleRespuesta.RegistroOrigen(
                                rs.getString("codigo"), rs.getString("tipo_documento"),
                                rs.getString("numero_documento"), rs.getString("nombre"),
                                rs.getString("domicilio_fiscal"), rs.getString("tipo_persona"),
                                rs.getString("estado"), rs.getString("nivel_homologacion")), maestro.getIdMaestro());
        return new CasoHomologacionDetalleRespuesta.Lado(maestro.getIdMaestro(), maestro.getSeudonimo(),
                maestro.getEstado(), registros);
    }

    private Usuario usuario(String nombreUsuario) {
        return usuarios.findByNombreUsuarioIgnoreCase(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("USUARIO_NO_ENCONTRADO",
                        "No existe el usuario " + nombreUsuario + "."));
    }

    private static RecursoNoEncontradoException noExiste(Long idCaso) {
        return new RecursoNoEncontradoException("CASO_NO_ENCONTRADO",
                "No existe el caso de homologación con id " + idCaso + ".");
    }
}
