package pe.municipalidad.tributario.constancia;

import pe.municipalidad.tributario.gestiontributaria.Predio;

public record PredioResumenVentanilla(
        String codigoPredio,
        String numeroCatastral,
        String ubicacion,
        String tipoPredio,
        String zona) {

    public static PredioResumenVentanilla desde(Predio predio) {
        return new PredioResumenVentanilla(
                predio.getCodigoPredio(),
                predio.getNumeroCatastral(),
                predio.getUbicacion(),
                predio.getTipoPredio() != null ? predio.getTipoPredio().name() : null,
                predio.getZona());
    }
}

