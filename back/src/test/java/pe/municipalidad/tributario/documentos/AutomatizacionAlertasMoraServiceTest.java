package pe.municipalidad.tributario.documentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.etl.TipoEjecucion;
import pe.municipalidad.tributario.soporte.PruebaInterna;

@PruebaInterna
@Transactional
class AutomatizacionAlertasMoraServiceTest {
    @Autowired AutomatizacionAlertasMoraService automatizacion;
    @Autowired LoteETLRepository lotes;
    @Autowired AvisoCorreoRepository avisos;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock reloj;

    @Test
    void soloProcesaLotesNuevosAlActivarseYConservaElAvanceAlPausarse() {
        assertThat(automatizacion.configuracion().habilitado()).isFalse();
        long antiguo = crearLote();
        automatizacion.cambiarEstado(true, "gerencia-prueba");
        automatizacion.procesarPendientes();
        assertThat(avisos.countByLoteIdLote(antiguo)).isZero();

        long nuevo = crearLote();
        LocalDate vencimiento = LocalDate.now(reloj).minusDays(1);
        jdbc.update("""
                INSERT INTO bronce.cobranza (id_lote, fila, id_obligacion, contribuyente_codigo,
                    fecha_vencimiento, insoluto)
                VALUES (?, 1, 987654321, '014502', ?, 150.00)
                """, nuevo, Date.valueOf(vencimiento));

        automatizacion.procesarPendientes();
        assertThat(avisos.countByLoteIdLote(nuevo)).isEqualTo(1);
        assertThat(automatizacion.procesos()).anyMatch(p -> p.idLote() == nuevo && p.ultimaFila() == 1);
        automatizacion.cambiarEstado(false, "gerencia-prueba");
        automatizacion.procesarPendientes();
        assertThat(automatizacion.procesos()).anyMatch(p -> p.idLote() == nuevo && p.estado().equals("EN_CURSO"));

        automatizacion.cambiarEstado(true, "gerencia-prueba");
        automatizacion.procesarPendientes();
        assertThat(automatizacion.procesos()).anyMatch(p -> p.idLote() == nuevo && p.estado().equals("COMPLETADO"));
        assertThat(avisos.countByLoteIdLote(nuevo)).isEqualTo(1);
    }

    private long crearLote() {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDateTime inicio = LocalDateTime.now(reloj).minusMinutes(1);
        LoteETL lote = lotes.saveAndFlush(new LoteETL("ps_etl_cobranza", hoy,
                TipoEjecucion.A_DEMANDA, inicio));
        lote.publicar(1, 1, LocalDateTime.now(reloj));
        return lotes.saveAndFlush(lote).getIdLote();
    }
}
