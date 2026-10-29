package pe.municipalidad.tributario.reporte;

/* Reportes del catálogo (RF-024, RF-050, RF-051). */
public enum TipoReporte {

    MOROSOS("Reporte de morosos",
            "Contribuyentes con cuotas vencidas impagas a la fecha de corte, con su deuda y etapa de cobranza."),
    PRICOS("Reporte de PRICOS",
            "Principales contribuyentes: monto emitido en el año igual o mayor al umbral configurado."),
    BUENOS_PAGADORES("Reporte de buenos pagadores",
            "Contribuyentes que pagaron a tiempo todas sus cuotas vencidas del año y no tienen deuda vencida.");

    private final String titulo;
    private final String descripcion;

    TipoReporte(String titulo, String descripcion) {
        this.titulo = titulo;
        this.descripcion = descripcion;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
