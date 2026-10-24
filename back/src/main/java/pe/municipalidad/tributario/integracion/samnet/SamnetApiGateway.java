package pe.municipalidad.tributario.integracion.samnet;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import pe.municipalidad.tributario.cobranza.EtapaCobranza;
import pe.municipalidad.tributario.cobranza.SeguimientoCobranza;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.contribuyente.DomicilioFiscal;
import pe.municipalidad.tributario.contribuyente.EstadoContribuyente;
import pe.municipalidad.tributario.contribuyente.TipoDocumento;
import pe.municipalidad.tributario.contribuyente.TipoPersona;
import pe.municipalidad.tributario.gestiontributaria.BeneficioTributario;
import pe.municipalidad.tributario.gestiontributaria.CanalPago;
import pe.municipalidad.tributario.gestiontributaria.DeclaracionJurada;
import pe.municipalidad.tributario.gestiontributaria.EstadoCuenta;
import pe.municipalidad.tributario.gestiontributaria.Pago;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.gestiontributaria.TipoBeneficio;
import pe.municipalidad.tributario.gestiontributaria.TipoPredio;
import pe.municipalidad.tributario.gestiontributaria.Titularidad;
import pe.municipalidad.tributario.obligacion.Obligacion;
import pe.municipalidad.tributario.obligacion.Tributo;

/*
 * Implementación de SamnetGateway que invoca los SP de Rentas (funciones samnet.ps_*). Hoy esos SP son
 * propios y leen la BD sintética; cuando haya acceso a SAMNET, solo cambia el origen de datos de esta clase.
 */
@Component
public class SamnetApiGateway implements SamnetGateway {

    /* Procedimientos que el ETL puede extraer y si reciben la fecha de corte. */
    private static final Map<String, Boolean> PROCEDIMIENTOS_ETL = Map.of(
            "ps_etl_contribuyentes", false,
            "ps_etl_predios", false,
            "ps_etl_cobranza", true,
            "ps_etl_pagos", true);

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public SamnetApiGateway(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public List<Contribuyente> buscarContribuyente(String codigoODocumento) {
        return invocar(() -> jdbc.query("SELECT * FROM samnet.ps_contribuyente(?)", CONTRIBUYENTE,
                codigoODocumento));
    }

    @Override
    public List<Contribuyente> buscarContribuyentes(String texto) {
        return invocar(() -> jdbc.query("SELECT * FROM samnet.ps_buscarcontribuyentes(?)", CONTRIBUYENTE, texto));
    }

    @Override
    public List<Predio> buscarPredio(String codigoPredio, String codigoContribuyente) {
        List<Map<String, Object>> filas = invocar(() -> jdbc.queryForList(
                "SELECT * FROM samnet.ps_predio(?, ?)", codigoPredio, codigoContribuyente));
        /* El SP devuelve una fila por titular; se agrupan por predio conservando el orden. */
        Map<String, List<Map<String, Object>>> porPredio = new LinkedHashMap<>();
        for (Map<String, Object> fila : filas) {
            porPredio.computeIfAbsent((String) fila.get("codigo_predio"), k -> new ArrayList<>()).add(fila);
        }
        return porPredio.values().stream().map(SamnetApiGateway::predioDesde).toList();
    }

    @Override
    public List<DeclaracionJurada> obtenerDeclaracionJurada(String codigoPredio) {
        return invocar(() -> jdbc.query("SELECT * FROM samnet.ps_declaracionjurada(?)",
                (rs, i) -> new DeclaracionJurada(
                        rs.getString("id_declaracion"),
                        rs.getString("codigo_predio"),
                        rs.getInt("anio"),
                        rs.getBigDecimal("autoavaluo"),
                        rs.getString("motivo"),
                        rs.getString("submotivo"),
                        rs.getString("tipo_declarante"),
                        rs.getString("estado"),
                        rs.getBoolean("es_vigente")),
                codigoPredio));
    }

    @Override
    public EstadoCuenta obtenerEstadoCuenta(String codigoContribuyente) {
        List<Obligacion> obligaciones = invocar(() -> jdbc.query("SELECT * FROM samnet.ps_ctacte(?)", OBLIGACION,
                codigoContribuyente));
        List<Pago> pagos = obtenerPagosYBeneficios(codigoContribuyente);
        return new EstadoCuenta(codigoContribuyente, LocalDate.now(reloj), obligaciones, pagos, false);
    }

    @Override
    public Optional<Obligacion> obtenerObligacion(long idObligacion) {
        return invocar(() -> jdbc.query("SELECT * FROM samnet.ps_obligacion(?)", OBLIGACION, idObligacion))
                .stream().findFirst();
    }

    @Override
    public List<SeguimientoCobranza> obtenerSeguimientoCobranza(long idObligacion) {
        return invocar(() -> jdbc.query("SELECT * FROM samnet.ps_seguimientocobranza(?)",
                (rs, i) -> new SeguimientoCobranza(
                        rs.getLong("id"),
                        rs.getLong("id_obligacion"),
                        EtapaCobranza.valueOf(rs.getString("etapa")),
                        fecha(rs, "fecha_inicio"),
                        rs.getString("documento_referencia"),
                        rs.getString("observacion"),
                        rs.getTimestamp("fecha_registro").toLocalDateTime()),
                idObligacion));
    }

    @Override
    public List<Pago> obtenerPagosYBeneficios(String codigoContribuyente) {
        return invocar(() -> jdbc.query("SELECT * FROM samnet.ps_resumenpagos(?)",
                (rs, i) -> new Pago(
                        rs.getString("id_pago"),
                        rs.getString("contribuyente_codigo"),
                        fecha(rs, "fecha_pago"),
                        CanalPago.valueOf(rs.getString("canal")),
                        rs.getBigDecimal("monto_pagado"),
                        rs.getBigDecimal("monto_correspondiente"),
                        rs.getString("beneficio_aplicado")),
                codigoContribuyente));
    }

    @Override
    public List<BeneficioTributario> verificarBeneficio(String codigoContribuyente) {
        return invocar(() -> jdbc.query("SELECT * FROM samnet.ps_verificarbeneficio(?, ?)",
                (rs, i) -> new BeneficioTributario(
                        rs.getString("id_beneficio"),
                        rs.getString("contribuyente_codigo"),
                        TipoBeneficio.valueOf(rs.getString("tipo")),
                        rs.getBigDecimal("porcentaje_descuento"),
                        fecha(rs, "vigencia_desde"),
                        fecha(rs, "vigencia_hasta")),
                codigoContribuyente, Date.valueOf(LocalDate.now(reloj))));
    }

    @Override
    public String registrarPredio(Predio predio, String codigoContribuyente) {
        Titularidad titular = predio.getTitulares().stream()
                .filter(t -> t.codigoContribuyente().equals(codigoContribuyente))
                .findFirst()
                .orElse(new Titularidad(codigoContribuyente, "PROPIETARIO", BigDecimal.valueOf(100)));
        return invocar(() -> jdbc.queryForObject(
                "SELECT samnet.ps_registrarpredio(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", String.class,
                predio.getCodigoPredio(), predio.getNumeroCatastral(), predio.getUbicacion(), predio.getUsoPredio(),
                predio.getTipoPredio().name(), predio.getAreaTerreno(), predio.getBaseImponible(),
                predio.getFechaAdquisicion() == null ? null : Date.valueOf(predio.getFechaAdquisicion()),
                predio.getZona(), codigoContribuyente, titular.condicionPropiedad(),
                titular.porcentajePropiedad()));
    }

    @Override
    public List<Map<String, Object>> extraerParaETL(String procedimiento, LocalDate fechaCorte) {
        Boolean recibeFecha = PROCEDIMIENTOS_ETL.get(procedimiento);
        if (recibeFecha == null) {
            throw new DatoInvalidoException("PROCEDIMIENTO_NO_AUTORIZADO",
                    "El procedimiento " + procedimiento + " no está autorizado para el ETL.");
        }
        /* El nombre viene de la lista blanca, nunca del usuario, por eso puede ir en el texto de la consulta. */
        return recibeFecha
                ? invocar(() -> jdbc.queryForList("SELECT * FROM samnet." + procedimiento + "(?)",
                        Date.valueOf(fechaCorte)))
                : invocar(() -> jdbc.queryForList("SELECT * FROM samnet." + procedimiento + "()"));
    }

    /* Una caída de la conexión o un tiempo de espera se informan como SAMNET no disponible (RNF-002). */
    private static <T> T invocar(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (DataAccessResourceFailureException | TransientDataAccessException ex) {
            throw new SamnetNoDisponibleException(ex);
        }
    }

    private static final RowMapper<Contribuyente> CONTRIBUYENTE = (rs, i) -> new Contribuyente(
            rs.getString("codigo"),
            TipoDocumento.valueOf(rs.getString("tipo_documento")),
            rs.getString("numero_documento"),
            rs.getString("nombre"),
            rs.getString("regimen"),
            TipoPersona.valueOf(rs.getString("tipo_persona")),
            rs.getString("condicion"),
            rs.getString("correo"),
            rs.getString("telefonos"),
            EstadoContribuyente.valueOf(rs.getString("estado")),
            new DomicilioFiscal(
                    rs.getString("domicilio_fiscal"),
                    rs.getString("dom_departamento"),
                    rs.getString("dom_provincia"),
                    rs.getString("dom_distrito"),
                    rs.getString("dom_via"),
                    rs.getString("dom_numero"),
                    rs.getString("dom_interior"),
                    rs.getString("dom_manzana"),
                    rs.getString("dom_lote")));

    private static final RowMapper<Obligacion> OBLIGACION = (rs, i) -> new Obligacion(
            rs.getLong("id_obligacion"),
            rs.getString("contribuyente_codigo"),
            rs.getString("codigo_predio"),
            Tributo.valueOf(rs.getString("tributo")),
            rs.getInt("anio"),
            rs.getInt("cuota"),
            fecha(rs, "fecha_vencimiento"),
            rs.getBigDecimal("monto_emitido"),
            rs.getBigDecimal("monto_pagado"),
            rs.getBigDecimal("porcentaje_descuento"),
            rs.getString("etapa_cobranza") == null ? null : EtapaCobranza.valueOf(rs.getString("etapa_cobranza")),
            fecha(rs, "fecha_etapa"));

    private static Predio predioDesde(List<Map<String, Object>> filas) {
        Map<String, Object> f = filas.getFirst();
        List<Titularidad> titulares = filas.stream()
                .filter(t -> t.get("contribuyente_codigo") != null)
                .map(t -> new Titularidad((String) t.get("contribuyente_codigo"),
                        (String) t.get("condicion_propiedad"), (BigDecimal) t.get("porcentaje_propiedad")))
                .toList();
        return new Predio(
                (String) f.get("codigo_predio"),
                (String) f.get("numero_catastral"),
                (String) f.get("ubicacion"),
                (String) f.get("uso_predio"),
                TipoPredio.valueOf((String) f.get("tipo_predio")),
                (BigDecimal) f.get("area_terreno"),
                (BigDecimal) f.get("base_imponible"),
                aFecha(f.get("fecha_adquisicion")),
                aFecha(f.get("fecha_baja")),
                (String) f.get("zona"),
                titulares);
    }

    private static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        Date valor = rs.getDate(columna);
        return valor == null ? null : valor.toLocalDate();
    }

    private static LocalDate aFecha(Object valor) {
        return valor == null ? null : ((Date) valor).toLocalDate();
    }
}
