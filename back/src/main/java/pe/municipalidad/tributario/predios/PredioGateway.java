package pe.municipalidad.tributario.predios;

import java.util.List;

public interface PredioGateway {
    List<Predio> buscar(String consulta);
}
