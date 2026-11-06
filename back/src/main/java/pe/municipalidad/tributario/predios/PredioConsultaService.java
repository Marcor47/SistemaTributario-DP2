package pe.municipalidad.tributario.predios;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.contribuyente.Contribuyente;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;

/*
 * HU002 (RF-002): búsqueda de predios en SAMNET por código predial, código catastral, dirección o titular (código,
 * nombre o documento), paginada para que un contribuyente con muchos predios se pueda recorrer y filtrar.
 */
@Service
public class PredioConsultaService {

    public static final int TAMANO_MAXIMO = 50;
    private static final int LONGITUD_MAXIMA_CONSULTA = 120;

    private final SamnetGateway samnet;

    public PredioConsultaService(SamnetGateway samnet) {
        this.samnet = samnet;
    }

    public PaginaPrediosRespuesta buscar(String consulta, int pagina, int tamano) {
        String texto = consulta == null ? "" : consulta.strip();
        if (texto.isEmpty() || texto.length() > LONGITUD_MAXIMA_CONSULTA) {
            throw new DatoInvalidoException("CONSULTA_INVALIDA",
                    "Ingrese entre 1 y " + LONGITUD_MAXIMA_CONSULTA + " caracteres para buscar predios.");
        }
        List<Predio> encontrados = samnet.buscarPredios(texto);
        int desde = Math.min(pagina * tamano, encontrados.size());
        int hasta = Math.min(desde + tamano, encontrados.size());
        Map<String, Optional<Contribuyente>> titulares = new HashMap<>();
        List<PredioRespuesta> contenido = encontrados.subList(desde, hasta).stream()
                .map(p -> respuesta(p, titulares))
                .toList();
        int totalPaginas = (encontrados.size() + tamano - 1) / tamano;
        return new PaginaPrediosRespuesta(contenido, pagina, tamano, encontrados.size(), totalPaginas);
    }

    /* Los titulares se repiten entre predios (una inmobiliaria con 30 departamentos): se consultan una sola vez. */
    private PredioRespuesta respuesta(Predio predio, Map<String, Optional<Contribuyente>> titulares) {
        List<PredioRespuesta.Titular> lista = predio.getTitulares().stream()
                .map(t -> {
                    Optional<Contribuyente> c = titulares.computeIfAbsent(t.codigoContribuyente(),
                            codigo -> samnet.buscarContribuyente(codigo).stream()
                                    .filter(x -> x.getCodigoContribuyente().equals(codigo)).findFirst());
                    return new PredioRespuesta.Titular(t.codigoContribuyente(),
                            c.map(Contribuyente::getNombreRazonSocial).orElse(null),
                            c.map(x -> x.getTipoDocumento().name()).orElse(null),
                            c.map(Contribuyente::getNumeroDocumento).orElse(null),
                            t.condicionPropiedad(), t.porcentajePropiedad());
                })
                .toList();
        return new PredioRespuesta(predio.getCodigoPredio(), predio.getNumeroCatastral(), predio.getUbicacion(),
                predio.getUsoPredio(), predio.getTipoPredio().name(), predio.getAreaTerreno(),
                predio.getBaseImponible(), predio.getFechaAdquisicion(), predio.getZona(), lista);
    }
}
