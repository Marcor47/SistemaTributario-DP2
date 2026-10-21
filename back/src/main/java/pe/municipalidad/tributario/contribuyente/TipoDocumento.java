package pe.municipalidad.tributario.contribuyente;

import java.util.regex.Pattern;

public enum TipoDocumento {

    DNI("Documento Nacional de Identidad", "\\d{8}", "8 dígitos"),
    RUC("Registro Único de Contribuyentes", "(10|15|17|20)\\d{9}", "11 dígitos que empiezan con 10, 15, 17 o 20"),
    CE("Carné de Extranjería", "[A-Z0-9]{9,12}", "de 9 a 12 caracteres alfanuméricos");

    private final String descripcion;
    private final Pattern formato;
    private final String formatoEsperado;

    TipoDocumento(String descripcion, String expresionFormato, String formatoEsperado) {
        this.descripcion = descripcion;
        this.formato = Pattern.compile(expresionFormato);
        this.formatoEsperado = formatoEsperado;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getFormatoEsperado() {
        return formatoEsperado;
    }

    public boolean esFormatoValido(String numeroDocumento) {
        return formato.matcher(numeroDocumento).matches();
    }
}
