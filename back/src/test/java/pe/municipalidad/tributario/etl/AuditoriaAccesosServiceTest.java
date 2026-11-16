package pe.municipalidad.tributario.etl;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import pe.municipalidad.tributario.seguridad.RegistroAuditoria;
import pe.municipalidad.tributario.seguridad.RegistroAuditoriaRepository;

class AuditoriaAccesosServiceTest {

    private final List<Object[]> llamadas = new ArrayList<>();

    private RegistroAuditoriaRepository repositorio(List<RegistroAuditoria> filas) {
        return (RegistroAuditoriaRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RegistroAuditoriaRepository.class}, (proxy, metodo, args) -> {
                    if ("findByAccionOrderByFechaHoraDescIdRegistroDesc".equals(metodo.getName())) {
                        llamadas.add(args);
                        return filas;
                    }
                    throw new UnsupportedOperationException(metodo.getName());
                });
    }

    @Test
    void consultaSoloReidentificacionesYMapeaLaRespuesta() {
        LocalDateTime fecha = LocalDateTime.of(2026, 11, 16, 9, 30);
        RegistroAuditoria sistema = new RegistroAuditoria(null, "REIDENTIFICAR", "CasoHomologacion", null, "caso:7",
                fecha);
        AuditoriaAccesosService servicio = new AuditoriaAccesosService(repositorio(List.of(sistema)));

        List<AccesoDatoRespuesta> accesos = servicio.recientes(50);

        assertEquals(1, accesos.size());
        AccesoDatoRespuesta acceso = accesos.get(0);
        assertEquals(fecha, acceso.fechaHora());
        assertEquals("CasoHomologacion", acceso.entidad());
        assertEquals("caso:7", acceso.detalle());
        assertNull(acceso.nombreUsuario());
        assertEquals("REIDENTIFICAR", llamadas.get(0)[0]);
        assertEquals(50, ((Pageable) llamadas.get(0)[1]).getPageSize());
    }

    @Test
    void acotaElLimite() {
        AuditoriaAccesosService servicio = new AuditoriaAccesosService(repositorio(List.of()));
        servicio.recientes(10_000);
        servicio.recientes(0);
        assertEquals(AuditoriaAccesosService.LIMITE_MAXIMO, ((Pageable) llamadas.get(0)[1]).getPageSize());
        assertEquals(1, ((Pageable) llamadas.get(1)[1]).getPageSize());
    }
}
