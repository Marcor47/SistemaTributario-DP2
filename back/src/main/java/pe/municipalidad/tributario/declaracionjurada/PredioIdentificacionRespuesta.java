package pe.municipalidad.tributario.declaracionjurada;

import pe.municipalidad.tributario.gestiontributaria.Predio;

public record PredioIdentificacionRespuesta(
        String codigoPredio,
        String numeroCatastral,
        String ubicacion) {

    public static PredioIdentificacionRespuesta desde(Predio predio) {
        return new PredioIdentificacionRespuesta(
                predio.getCodigoPredio(),
                predio.getNumeroCatastral(),
                predio.getUbicacion());
    }
}
