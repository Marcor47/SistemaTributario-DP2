import { moneda } from '../utils/formato.js'

export default function EtiquetaDeudaVencida({ saldo, diasVencida }) {
  const dias = Number(diasVencida)
  const textoDias = Number.isFinite(dias)
    ? `${dias} ${dias === 1 ? 'día vencida' : 'días vencida'}`
    : 'Días no disponibles'

  return (
    <span className="etiqueta-deuda-vencida">
      <span>{moneda(saldo)}</span>
      <span aria-hidden="true">·</span>
      <span>{textoDias}</span>
    </span>
  )
}
