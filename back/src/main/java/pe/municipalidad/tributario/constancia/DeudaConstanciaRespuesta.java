package pe.municipalidad.tributario.constancia;

import java.math.BigDecimal;
import java.util.List;
import pe.municipalidad.tributario.estadocuenta.EstadoCuentaFilaRespuesta;

public record DeudaConstanciaRespuesta(BigDecimal total, List<EstadoCuentaFilaRespuesta> obligaciones,
        boolean puedeSolicitar) {
    public DeudaConstanciaRespuesta(BigDecimal total, List<EstadoCuentaFilaRespuesta> obligaciones) {
        this(total, obligaciones, total.signum() == 0);
    }
}
