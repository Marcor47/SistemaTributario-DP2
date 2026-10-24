package pe.municipalidad.tributario.gestiontributaria;

import java.math.BigDecimal;
import java.time.LocalDate;

/* Beneficio sobre los arbitrios (amnistía o particular) vigente en SAMNET (RF-036, RF-062). */
public record BeneficioTributario(
        String idBeneficio,
        String codigoContribuyente,
        TipoBeneficio tipo,
        BigDecimal porcentajeDescuento,
        LocalDate vigenciaDesde,
        LocalDate vigenciaHasta) {

    public boolean estaVigente(LocalDate fecha) {
        return !fecha.isBefore(vigenciaDesde) && !fecha.isAfter(vigenciaHasta);
    }
}
