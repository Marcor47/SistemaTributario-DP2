package pe.municipalidad.tributario.integracion.samnet;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import pe.municipalidad.tributario.cobranza.SeguimientoCobranza;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.gestiontributaria.BeneficioTributario;
import pe.municipalidad.tributario.gestiontributaria.DeclaracionJurada;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.gestiontributaria.Pago;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.obligacion.Obligacion;

/*
 * Única vía de acceso a SAMNET: la capa de servicios de la Municipalidad y sus SP autorizados (RNF-001).
 * Sin acceso a los SP reales, la implementación consulta SP propios con el mismo contrato sobre la BD
 * sintética. Si SAMNET no responde, las operaciones lanzan SamnetNoDisponibleException.
 */
public interface SamnetGateway {

    /* RF-001: por código de contribuyente o número de documento. */
    List<Contribuyente> buscarContribuyente(String codigoODocumento);

    /* HU004: búsqueda parcial por código, nombre o documento (operación agregada al modelo). */
    List<Contribuyente> buscarContribuyentes(String texto);

    /* RF-002: por código predial o por contribuyente titular; uno de los dos puede ser nulo. */
    List<Predio> buscarPredio(String codigoPredio, String codigoContribuyente);

    /* RF-003: declaraciones juradas del predio; la vigente primero. */
    List<DeclaracionJurada> obtenerDeclaracionJurada(String codigoPredio);

    /* RF-004, RF-018: obligaciones y pagos del contribuyente a la fecha de corte de hoy. */
    EstadoCuenta obtenerEstadoCuenta(String codigoContribuyente);

    /* RF-014: una obligación puntual y su historial de cobranza (operaciones agregadas al modelo). */
    Optional<Obligacion> obtenerObligacion(long idObligacion);

    List<SeguimientoCobranza> obtenerSeguimientoCobranza(long idObligacion);

    /* RF-062: pagos con lo que correspondía pagar y el beneficio aplicado. */
    List<Pago> obtenerPagosYBeneficios(String codigoContribuyente);

    /* RF-036: beneficios vigentes hoy. */
    List<BeneficioTributario> verificarBeneficio(String codigoContribuyente);

    /** Asignación explícita del beneficio a cada obligación según SAMNET. */
    Map<Long, String> obtenerBeneficiosPorObligacion(String codigoContribuyente);

    /** Registra un pago autorizado en la base PostgreSQL y cancela sus obligaciones de forma atómica. */
    String registrarPagoEnLinea(String codigoContribuyente, long idTransaccion, LocalDate fechaPago);

    /* RF-048, RF-056: registra o edita el predio y su titular; devuelve el código predial. */
    String registrarPredio(Predio predio, String codigoContribuyente);

    /* RF-038: extracción de un SP de Rentas para el ETL. */
    List<Map<String, Object>> extraerParaETL(String procedimiento, LocalDate fechaCorte);
}
