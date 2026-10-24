package pe.municipalidad.tributario.reclamo;

import java.util.Set;

/*
 * A qué se refiere el reclamo (RF-064). El formulario es el mismo para todos; los tipos disponibles dependen
 * de quién reclama.
 */
public enum TipoReclamo {

    COBRANZA(Set.of(TipoReclamante.CONTRIBUYENTE, TipoReclamante.TRABAJADOR)),
    SERVICIO(Set.of(TipoReclamante.CONTRIBUYENTE)),
    ATENCION(Set.of(TipoReclamante.CONTRIBUYENTE)),
    AREA_RELACIONADA(Set.of(TipoReclamante.TRABAJADOR)),
    DOCUMENTO(Set.of(TipoReclamante.TRABAJADOR)),
    OTRO(Set.of(TipoReclamante.CONTRIBUYENTE, TipoReclamante.TRABAJADOR));

    private final Set<TipoReclamante> reclamantes;

    TipoReclamo(Set<TipoReclamante> reclamantes) {
        this.reclamantes = reclamantes;
    }

    public boolean permitidoPara(TipoReclamante reclamante) {
        return reclamantes.contains(reclamante);
    }
}
