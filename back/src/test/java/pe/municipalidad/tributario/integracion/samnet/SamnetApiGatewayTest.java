package pe.municipalidad.tributario.integracion.samnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.gestiontributaria.DeclaracionJurada;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;
import pe.municipalidad.tributario.gestiontributaria.Titularidad;
import pe.municipalidad.tributario.integracion.autenticacion.AutenticacionMunicipalGateway;
import pe.municipalidad.tributario.soporte.PruebaIntegracion;

/*
 * SamnetGateway contra los SP emulados (samnet.ps_*) y los datos sintéticos de R__datos_prueba_01 a 04.
 * Fecha fija de los tests: 2026-10-19.
 */
@PruebaIntegracion
@Transactional
class SamnetApiGatewayTest {

    @Autowired
    private SamnetGateway samnet;

    @Autowired
    private AutenticacionMunicipalGateway autenticacion;

    @Test
    void buscaContribuyentePorCodigoOPorDocumentoConSuDomicilioDesglosado() {
        List<Contribuyente> porDocumento = samnet.buscarContribuyente("41028765");
        List<Contribuyente> porCodigo = samnet.buscarContribuyente("014502");

        assertThat(porDocumento).hasSize(1);
        assertThat(porCodigo).hasSize(1);
        Contribuyente contribuyente = porDocumento.getFirst();
        assertThat(contribuyente.getCodigoContribuyente()).isEqualTo("014502");
        assertThat(contribuyente.getRegimen()).isEqualTo("GENERAL");
        assertThat(contribuyente.getDomicilioFiscal().direccion()).isEqualTo("Av. La Marina 1450, San Miguel");
        assertThat(contribuyente.getDomicilioFiscal().distrito()).isEqualTo("SAN MIGUEL");
    }

    @Test
    void agrupaLosTitularesDeUnPredioEnCopropiedad() {
        List<Predio> predios = samnet.buscarPredio("058934-01", null);

        assertThat(predios).hasSize(1);
        assertThat(predios.getFirst().getTitulares()).extracting(Titularidad::codigoContribuyente)
                .containsExactly("058934", "061177");
        assertThat(predios.getFirst().validar()).isEmpty();
        assertThat(samnet.buscarPredio(null, "061177")).extracting(Predio::getCodigoPredio)
                .containsExactly("058934-01");
    }

    @Test
    void devuelveLaDeclaracionJuradaVigentePrimero() {
        List<DeclaracionJurada> declaraciones = samnet.obtenerDeclaracionJurada("014502-01");

        assertThat(declaraciones).hasSize(2);
        assertThat(declaraciones.getFirst().esVigente()).isTrue();
        assertThat(declaraciones.getFirst().anio()).isEqualTo(2024);
    }

    @Test
    void estadoDeCuentaIncluyeObligacionesPagosYTotales() {
        EstadoCuenta estadoCuenta = samnet.obtenerEstadoCuenta("021390");

        assertThat(estadoCuenta.obtenerFechaCorte()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(estadoCuenta.getObligaciones()).hasSize(12);
        assertThat(estadoCuenta.getTotalDeuda()).isEqualByComparingTo("6907.68");
        assertThat(estadoCuenta.getPagos()).isNotEmpty();
        assertThat(estadoCuenta.getPagos()).allSatisfy(p -> assertThat(p.diferencia()).isZero());
    }

    @Test
    void obligacionTraeSuEtapaDeCobranzaMasReciente() {
        long id = samnet.obtenerEstadoCuenta("021390").desglosar(null, 2025).stream()
                .filter(o -> o.getCuota() == 4 && o.getTributo().name().equals("IMPUESTO_PREDIAL"))
                .findFirst().orElseThrow().getIdObligacion();

        assertThat(samnet.obtenerObligacion(id)).hasValueSatisfying(
                o -> assertThat(o.getEtapaCobranza()).hasToString("COACTIVA"));
        assertThat(samnet.obtenerSeguimientoCobranza(id)).hasSize(4);
        assertThat(samnet.obtenerObligacion(999_999)).isEmpty();
    }

    @Test
    void verificaSoloLosBeneficiosVigentes() {
        assertThat(samnet.verificarBeneficio("033218")).hasSize(1);
        assertThat(samnet.verificarBeneficio("058934")).hasSize(1);
        assertThat(samnet.verificarBeneficio("014502")).isEmpty();
    }

    @Test
    void registraUnPredioNuevoConSuTitular() {
        Predio nuevo = new Predio(null, "150136-010-001", "Av. Libertad 500, San Miguel", "Vivienda",
                TipoPredio.URBANO, new BigDecimal("100.00"), new BigDecimal("150000.00"), LocalDate.of(2026, 9, 1),
                null, "Z1", List.of(new Titularidad("014502", "PROPIETARIO", new BigDecimal("100"))));

        String codigo = samnet.registrarPredio(nuevo, "014502");

        assertThat(codigo).isNotBlank();
        assertThat(samnet.buscarPredio(null, "014502")).extracting(Predio::getCodigoPredio).contains(codigo);
    }

    @Test
    void elEtlSoloExtraeProcedimientosAutorizados() {
        assertThat(samnet.extraerParaETL("ps_etl_cobranza", LocalDate.of(2026, 10, 19))).isNotEmpty();
        assertThat(samnet.extraerParaETL("ps_etl_predios", null)).isNotEmpty();
        assertThatThrownBy(() -> samnet.extraerParaETL("ps_login", LocalDate.of(2026, 10, 19)))
                .isInstanceOf(DatoInvalidoException.class);
    }

    @Test
    @Sql(statements = """
            INSERT INTO samnet.credencial_web (contribuyente_codigo, clave_hash)
            VALUES ('014502', public.crypt('clave-de-prueba', public.gen_salt('bf', 4)));
            """)
    void autenticaConElServicioMunicipalSinExponerLaClave() {
        assertThat(autenticacion.autenticar("41028765", "clave-de-prueba"))
                .hasValueSatisfying(c -> assertThat(c.codigoContribuyente()).isEqualTo("014502"));
        assertThat(autenticacion.autenticar("41028765", "otra-clave")).isEmpty();
        assertThat(autenticacion.validarIdentidad("41028765")).contains("GARCÍA TORRES, MARÍA ELENA");
    }
}
