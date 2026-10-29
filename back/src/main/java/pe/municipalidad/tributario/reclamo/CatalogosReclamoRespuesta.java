package pe.municipalidad.tributario.reclamo;

import java.util.List;

public record CatalogosReclamoRespuesta(List<AreaMunicipalRespuesta> areas,
        List<ResponsableReclamoRespuesta> responsables) {
}
