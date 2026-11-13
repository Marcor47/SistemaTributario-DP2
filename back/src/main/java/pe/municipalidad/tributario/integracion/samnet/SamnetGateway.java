package pe.municipalidad.tributario.integracion.samnet;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import pe.municipalidad.tributario.cobranza.SeguimientoCobranza;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.gestiontributaria.BeneficioTributario;
import pe.municipalidad.tributario.gestiontributaria.CarteraTributo;
import pe.municipalidad.tributario.gestiontributaria.DeclaracionJurada;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.gestiontributaria.Pago;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.gestiontributaria.Titularidad;
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

    /* HU018: zonas registradas en los predios, para el selector de los reportes por zona. */
    List<String> obtenerZonas();

    /* Tributos con cuotas emitidas, para el selector de los reportes. */
    List<String> obtenerTributos();

    /* Años con cuotas emitidas, del más reciente al más antiguo. */
    List<Integer> obtenerAniosEmision();
    /* Auditoría 2026-11: búsqueda parcial por código predial, dirección o titular, para listas largas. */
    List<Predio> buscarPredios(String texto);

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

    /* HU036 / RF-048: registra un predio NUEVO. Si el código ya existe lanza ConflictoException sin modificarlo. */
    String registrarPredioNuevo(Predio predio, String codigoContribuyente);

    /*
     * HU043 / RF-056: traspasa un predio existente al adquirente, que queda como único titular. Si el predio no existe
     * lanza RecursoNoEncontradoException sin modificar nada.
     */
    String traspasarPredio(String codigoPredio, Titularidad adquirente);

    /* RF-024, RF-050, RF-051: cartera por contribuyente y tributo para los reportes del catálogo (HU006). */
    List<CarteraTributo> obtenerCartera(LocalDate fechaCorte, int anioDesde, int anioHasta, String beneficio);

    /* RF-038: extracción de un SP de Rentas para el ETL. */
    List<Map<String, Object>> extraerParaETL(String procedimiento, LocalDate fechaCorte);

    List<Map<String, Object>> obtenerVencimientos(LocalDate desde, LocalDate hasta);
}
