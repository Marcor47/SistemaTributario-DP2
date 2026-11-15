package pe.municipalidad.tributario.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.municipalidad.tributario.analitica.DimContribuyente;
import pe.municipalidad.tributario.etl.ReidentificacionService;
import pe.municipalidad.tributario.etl.TipoEntidad;
import pe.municipalidad.tributario.ia.AsignacionSegmento;
import pe.municipalidad.tributario.ia.AsignacionSegmentoRepository;
import pe.municipalidad.tributario.ia.EstadoSegmento;
import pe.municipalidad.tributario.ia.Segmento;
import pe.municipalidad.tributario.ia.SegmentoRepository;
import pe.municipalidad.tributario.seguridad.Usuario;

class SegmentoAccesoServiceTest {

    @Test
    void resuelveElSegmentoPublicadoDelContribuyenteYListaLasOpciones() {
        var reidentificacion = mock(ReidentificacionService.class);
        var asignaciones = mock(AsignacionSegmentoRepository.class);
        var segmentos = mock(SegmentoRepository.class);
        var usuario = mock(Usuario.class);
        var contribuyente = mock(DimContribuyente.class);
        var asignacion = mock(AsignacionSegmento.class);
        var segmento = mock(Segmento.class);

        when(reidentificacion.seudonimosDe(any(), eq(TipoEntidad.CONTRIBUYENTE), eq(usuario)))
                .thenReturn(Map.of("080245", "SEUD-1"));
        when(contribuyente.getSeudonimoContribuyente()).thenReturn("SEUD-1");
        when(segmento.getNombre()).thenReturn("Riesgo alto");
        when(asignacion.getContribuyente()).thenReturn(contribuyente);
        when(asignacion.getSegmento()).thenReturn(segmento);
        when(asignaciones.publicadosPorSeudonimos(any(), eq(EstadoSegmento.PUBLICADO)))
                .thenReturn(List.of(asignacion));
        when(segmentos.buscarPorEstado(EstadoSegmento.PUBLICADO)).thenReturn(List.of(segmento));

        var servicio = new SegmentoAccesoService(reidentificacion, asignaciones, segmentos);

        assertThat(servicio.porCodigo(List.of("080245"), usuario)).containsEntry("080245", "Riesgo alto");
        assertThat(servicio.publicados()).containsExactly("Riesgo alto");
    }
}
