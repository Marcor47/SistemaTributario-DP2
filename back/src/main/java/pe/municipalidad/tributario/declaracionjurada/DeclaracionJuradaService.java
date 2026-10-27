package pe.municipalidad.tributario.declaracionjurada;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.municipalidad.tributario.common.ConflictoException;
import pe.municipalidad.tributario.common.DatoInvalidoException;
import pe.municipalidad.tributario.common.RecursoNoEncontradoException;
import pe.municipalidad.tributario.gestiontributaria.DeclaracionJurada;
import pe.municipalidad.tributario.gestiontributaria.Predio;
import pe.municipalidad.tributario.integracion.samnet.SamnetGateway;

@Service
public class DeclaracionJuradaService {

    private static final int LONGITUD_MAXIMA_CODIGO = 12;

    private final SamnetGateway samnet;
    private final Clock reloj;

    public DeclaracionJuradaService(SamnetGateway samnet, Clock reloj) {
        this.samnet = samnet;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public DeclaracionJuradaRespuesta consultar(String codigoPredio) {
        String codigo = normalizarCodigo(codigoPredio);
        Predio predio = obtenerPredio(codigo);
        List<DeclaracionJurada> declaraciones = samnet.obtenerDeclaracionJurada(codigo);

        List<AdvertenciaRespuesta> advertencias = new ArrayList<>();
        List<DeclaracionJurada> validas = filtrarValidas(codigo, declaraciones, advertencias);

        DeclaracionJurada vigente = resolverVigente(codigo, validas);
        if (validas.isEmpty()) {
            advertencias.add(AdvertenciaRespuesta.de("SIN_DECLARACIONES",
                    "El predio no tiene declaraciones juradas registradas en SAMNET."));
        } else if (vigente == null) {
            advertencias.add(AdvertenciaRespuesta.de("SIN_DECLARACION_VIGENTE",
                    "SAMNET no reporta una declaración jurada vigente para el predio."));
        }
        if (validas.stream().anyMatch(DeclaracionJuradaService::tieneDatosSospechosos)) {
            advertencias.add(AdvertenciaRespuesta.de("DATO_SAMNET_SOSPECHOSO",
                    "SAMNET devolvió una declaración jurada con año o autoavalúo fuera de rango."));
        }

        return new DeclaracionJuradaRespuesta(
                PredioIdentificacionRespuesta.desde(predio),
                vigente == null ? null : DeclaracionJuradaItemRespuesta.desde(vigente, true),
                historial(validas, vigente),
                LocalDate.now(reloj),
                List.copyOf(advertencias));
    }

    private static String normalizarCodigo(String codigoPredio) {
        String codigo = codigoPredio == null ? "" : codigoPredio.trim();
        if (codigo.isEmpty()) {
            throw new DatoInvalidoException("CODIGO_PREDIO_INVALIDO", "El código predial es obligatorio.");
        }
        if (codigo.length() > LONGITUD_MAXIMA_CODIGO) {
            throw new DatoInvalidoException("CODIGO_PREDIO_INVALIDO",
                    "El código predial no puede superar los " + LONGITUD_MAXIMA_CODIGO + " caracteres.");
        }
        return codigo;
    }

    private Predio obtenerPredio(String codigo) {
        return samnet.buscarPredio(codigo, null).stream()
                .filter(predio -> codigo.equals(predio.getCodigoPredio()))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("PREDIO_NO_ENCONTRADO",
                        "No existe el predio con código " + codigo + "."));
    }

    private static List<DeclaracionJurada> filtrarValidas(String codigo,
            List<DeclaracionJurada> declaraciones, List<AdvertenciaRespuesta> advertencias) {
        List<DeclaracionJurada> validas = new ArrayList<>();
        boolean descartada = false;
        for (DeclaracionJurada declaracion : declaraciones) {
            if (declaracion == null || declaracion.idDeclaracion() == null
                    || declaracion.idDeclaracion().isBlank() || declaracion.codigoPredio() == null
                    || declaracion.codigoPredio().isBlank() || !codigo.equals(declaracion.codigoPredio())) {
                descartada = true;
                continue;
            }
            validas.add(declaracion);
        }
        if (descartada) {
            advertencias.add(AdvertenciaRespuesta.de("REGISTRO_SAMNET_INVALIDO",
                    "SAMNET devolvió registros de declaración jurada incompletos o de otro predio."));
        }
        return validas;
    }

    private static DeclaracionJurada resolverVigente(String codigo, List<DeclaracionJurada> validas) {
        List<DeclaracionJurada> vigentes = validas.stream()
                .filter(DeclaracionJurada::esVigente)
                .toList();
        if (vigentes.size() > 1) {
            throw new ConflictoException("DECLARACION_VIGENTE_AMBIGUA",
                    "SAMNET devolvió más de una declaración jurada vigente para el predio " + codigo + ".");
        }
        return vigentes.isEmpty() ? null : vigentes.getFirst();
    }

    private static List<DeclaracionJuradaItemRespuesta> historial(List<DeclaracionJurada> validas,
            DeclaracionJurada vigente) {
        Comparator<DeclaracionJurada> orden = Comparator
                .comparingInt(DeclaracionJurada::anio)
                .reversed()
                .thenComparing(DeclaracionJurada::idDeclaracion, Comparator.reverseOrder());
        return validas.stream()
                .filter(declaracion -> vigente == null
                        || !declaracion.idDeclaracion().equals(vigente.idDeclaracion()))
                .sorted(orden)
                .map(declaracion -> DeclaracionJuradaItemRespuesta.desde(declaracion, false))
                .toList();
    }

    private static boolean tieneDatosSospechosos(DeclaracionJurada declaracion) {
        return declaracion.anio() <= 0 || declaracion.autoavaluo() == null
                || declaracion.autoavaluo().signum() < 0;
    }
}
