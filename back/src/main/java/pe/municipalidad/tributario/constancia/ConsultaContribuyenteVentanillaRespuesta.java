package pe.municipalidad.tributario.constancia;

import java.util.List;
import pe.municipalidad.tributario.contribuyente.Contribuyente;

/*
 * Consulta previa en ventanilla para emisión presencial de constancia de no adeudo (HU042, RF-055, RF-034).
 * Presenta la situación del contribuyente: sus datos, sus predios registrados para selección y su estado de deuda.
 */
public record ConsultaContribuyenteVentanillaRespuesta(
        DatosContribuyenteDto contribuyente,
        List<PredioResumenVentanilla> predios,
        DeudaConstanciaRespuesta deuda) {

    public record DatosContribuyenteDto(
            String codigoContribuyente,
            String tipoDocumento,
            String numeroDocumento,
            String nombreRazonSocial) {

        public static DatosContribuyenteDto desde(Contribuyente c) {
            return new DatosContribuyenteDto(
                    c.getCodigoContribuyente(),
                    c.getTipoDocumento().name(),
                    c.getNumeroDocumento(),
                    c.getNombreRazonSocial());
        }
    }
}

