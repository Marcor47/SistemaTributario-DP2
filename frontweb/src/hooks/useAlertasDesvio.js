import { useCallback, useEffect, useMemo, useState } from 'react'
import { listarAlertasDesvio } from '../services/alertaDesvioService.js'
import { filtrarAlertasPorFiltros, resumirAlertas } from '../utils/alertas.js'

/*
 * HU039: carga las alertas activas y las somete a los filtros del dashboard.
 * El filtrado es local para que cambiar año, periodo, zona, tributo o segmento actualice
 * las tarjetas de inmediato; el botón Actualizar del dashboard incrementa `revision` y
 * vuelve a consultar el endpoint.
 */
export function useAlertasDesvio({
  activo = true,
  filtros = {},
  indicadorCodigo = '',
  periodo = '',
  segmentos = [],
  revision = 0,
} = {}) {
  const [estado, setEstado] = useState({ alertas: [], error: null, claveCarga: '' })
  const [revisionLocal, setRevisionLocal] = useState(0)
  const claveCarga = `${revision}:${revisionLocal}`

  useEffect(() => {
    if (!activo) return undefined

    const controlador = new AbortController()
    listarAlertasDesvio({ signal: controlador.signal })
      .then((alertas) => {
        if (!controlador.signal.aborted) {
          setEstado({
            alertas: Array.isArray(alertas) ? alertas : [],
            error: null,
            claveCarga,
          })
        }
      })
      .catch((error) => {
        if (!controlador.signal.aborted) {
          setEstado({ alertas: [], error, claveCarga })
        }
      })

    return () => controlador.abort()
  }, [activo, claveCarga])

  const alertas = useMemo(
    () => filtrarAlertasPorFiltros(
      activo ? estado.alertas : [],
      { ...filtros, indicadorCodigo },
      { periodo, segmentos },
    ),
    [activo, estado.alertas, filtros, indicadorCodigo, periodo, segmentos],
  )

  const resumen = useMemo(() => resumirAlertas(alertas), [alertas])
  const recargar = useCallback(() => setRevisionLocal((valor) => valor + 1), [])
  const cargando = activo && estado.claveCarga !== claveCarga
  const error = activo && estado.claveCarga === claveCarga ? estado.error : null

  return {
    alertas,
    total: resumen.total,
    indicadoresEnDesvio: resumen.indicadores,
    ultimaCorte: resumen.ultimaCorte,
    hayAlertas: activo && estado.alertas.length > 0,
    cargando,
    error,
    recargar,
  }
}
