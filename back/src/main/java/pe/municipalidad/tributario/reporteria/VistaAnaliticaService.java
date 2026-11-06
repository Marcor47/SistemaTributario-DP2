package pe.municipalidad.tributario.reporteria;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.NoAutenticadoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.seguridad.Usuario;
import pe.municipalidad.tributario.seguridad.UsuarioRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/* Vistas guardadas de la plataforma analítica. Guardar con un nombre que ya existe actualiza esa vista. */
@Service
public class VistaAnaliticaService {

    private static final int TAMANO_MAXIMO_ESTADO = 20_000;

    private final VistaAnaliticaRepository vistas;
    private final UsuarioRepository usuarios;
    private final JsonMapper json;
    private final Clock reloj;

    public VistaAnaliticaService(VistaAnaliticaRepository vistas, UsuarioRepository usuarios, JsonMapper json,
            Clock reloj) {
        this.vistas = vistas;
        this.usuarios = usuarios;
        this.json = json;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<VistaAnaliticaRespuesta> listar(String nombreUsuario) {
        return vistas.findByUsuarioOrderByFechaActualizacionDesc(usuario(nombreUsuario)).stream()
                .map(this::respuesta).toList();
    }

    @Transactional
    public VistaAnaliticaRespuesta guardar(GuardarVistaAnaliticaPeticion peticion, String nombreUsuario) {
        Usuario usuario = usuario(nombreUsuario);
        String nombre = peticion.nombre().strip();
        String estado = json.writeValueAsString(peticion.estado());
        if (estado.length() > TAMANO_MAXIMO_ESTADO) {
            throw new DatoInvalidoException("VISTA_DEMASIADO_GRANDE", "La vista tiene demasiada información para guardarse.");
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        VistaAnalitica vista = vistas.findByUsuarioAndNombre(usuario, nombre).orElse(null);
        if (vista == null) {
            vista = vistas.save(new VistaAnalitica(usuario, nombre, estado, ahora));
        } else {
            vista.actualizar(estado, ahora);
        }
        return respuesta(vista);
    }

    @Transactional
    public void eliminar(Long idVista, String nombreUsuario) {
        VistaAnalitica vista = vistas.findByIdVistaAndUsuario(idVista, usuario(nombreUsuario))
                .orElseThrow(() -> new RecursoNoEncontradoException("VISTA_NO_ENCONTRADA", "No se encontró la vista."));
        vistas.delete(vista);
    }

    private VistaAnaliticaRespuesta respuesta(VistaAnalitica vista) {
        return new VistaAnaliticaRespuesta(vista.getIdVista(), vista.getNombre(), estado(vista.getEstado()),
                vista.getFechaActualizacion());
    }

    private Map<String, Object> estado(String valor) {
        try {
            return json.readValue(valor, new TypeReference<Map<String, Object>>() { });
        } catch (JacksonException ex) {
            return Map.of();
        }
    }

    private Usuario usuario(String nombreUsuario) {
        return usuarios.findByNombreUsuario(nombreUsuario).orElseThrow(() ->
                new NoAutenticadoException("SESION_REQUERIDA", "Inicie sesión como usuario interno."));
    }
}
