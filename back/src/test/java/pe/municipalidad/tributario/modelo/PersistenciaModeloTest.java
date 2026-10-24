package pe.municipalidad.tributario.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.etl.LoteETL;
import pe.municipalidad.tributario.etl.LoteETLRepository;
import pe.municipalidad.tributario.etl.ReglaCalidadRepository;
import pe.municipalidad.tributario.etl.ResultadoCalidad;
import pe.municipalidad.tributario.etl.ResultadoRegla;
import pe.municipalidad.tributario.etl.TipoEjecucion;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;
import pe.municipalidad.tributario.indicador.AlcanceIndicador;
import pe.municipalidad.tributario.indicador.IndicadorRepository;
import pe.municipalidad.tributario.indicador.ValorIndicador;
import pe.municipalidad.tributario.indicador.ValorIndicadorRepository;
import pe.municipalidad.tributario.seguridad.NombreRol;
import pe.municipalidad.tributario.seguridad.RegistroAuditoria;
import pe.municipalidad.tributario.seguridad.RegistroAuditoriaRepository;
import pe.municipalidad.tributario.seguridad.RolRepository;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;
import pe.municipalidad.tributario.tramitepredio.RequisitoDocumentalRepository;
import pe.municipalidad.tributario.tramitepredio.TipoTramitePredio;

/* Persistencia del modelo de análisis: catálogos iniciales, relaciones y registros inalterables. */
@PruebaIntegracion
@Transactional
class PersistenciaModeloTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RolRepository roles;

    @Autowired
    private RegistroAuditoriaRepository auditoria;

    @Autowired
    private LoteETLRepository lotes;

    @Autowired
    private ReglaCalidadRepository reglas;

    @Autowired
    private IndicadorRepository indicadores;

    @Autowired
    private ValorIndicadorRepository valores;

    @Autowired
    private RequisitoDocumentalRepository requisitos;

    @Test
    void cargaLosSeisRolesYLasCincoReglasDeCalidad() {
        assertThat(roles.findAll()).hasSize(6);
        assertThat(roles.findByNombre(NombreRol.VENTANILLA)).isPresent();
        assertThat(reglas.findAll()).hasSize(5);
        assertThat(requisitos.findByTipoTramiteAndTipoPredio(TipoTramitePredio.TRASPASO, TipoPredio.URBANO))
                .hasSize(2);
    }

    @Test
    void elRegistroDeAuditoriaNoSePuedeModificar() {
        RegistroAuditoria registro = registrarAuditoria();

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE seguridad.registro_auditoria SET accion = 'X' WHERE id_registro = ?", registro.getIdRegistro()))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("no se puede modificar");
    }

    @Test
    void elRegistroDeAuditoriaNoSePuedeBorrar() {
        RegistroAuditoria registro = registrarAuditoria();

        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM seguridad.registro_auditoria WHERE id_registro = ?", registro.getIdRegistro()))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("no se puede modificar ni eliminar");
    }

    private RegistroAuditoria registrarAuditoria() {
        return auditoria.saveAndFlush(new RegistroAuditoria(null, "PRUEBA", "seguridad.rol", null, "{}",
                LocalDateTime.of(2026, 10, 19, 12, 0)));
    }

    @Test
    void unLoteGuardaSusResultadosDeCalidadYLosValoresQueCalcula() {
        LoteETL lote = new LoteETL("ps_etl_cobranza", LocalDate.of(2026, 10, 19), TipoEjecucion.A_DEMANDA,
                LocalDateTime.of(2026, 10, 19, 2, 0));
        lote.registrarResultado(new ResultadoRegla(reglas.findByCodigo("R1").orElseThrow(), 144, 0,
                ResultadoCalidad.CUMPLE, null));
        lote.publicar(144, 144, LocalDateTime.of(2026, 10, 19, 2, 5));
        lotes.saveAndFlush(lote);

        valores.saveAndFlush(new ValorIndicador(indicadores.findById("INDICE_MOROSIDAD").orElseThrow(), lote, 2026,
                10, AlcanceIndicador.TOTAL, null, new BigDecimal("13.2500")));

        Integer resultados = jdbc.queryForObject(
                "SELECT count(*) FROM analitica.resultado_regla WHERE id_lote = ?", Integer.class, lote.getIdLote());
        List<ValorIndicador> guardados = valores.findAll();
        assertThat(resultados).isEqualTo(1);
        assertThat(guardados).hasSize(1);
        assertThat(guardados.getFirst().getFechaCorte()).isEqualTo(LocalDate.of(2026, 10, 19));
    }
}
