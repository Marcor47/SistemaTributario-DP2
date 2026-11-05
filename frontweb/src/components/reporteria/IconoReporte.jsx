const TRAZOS = {
  barras: <path d="M3.5 13V8.5M8 13V3.5M12.5 13V6.5" strokeWidth="2.6" strokeLinecap="round" />,
  barrash: <path d="M3 4h6M3 8h10M3 12h4" strokeWidth="2.6" strokeLinecap="round" />,
  linea: <path d="M2 12l4-5 3 3 5-6.5" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />,
  area: <><path d="M2 13.5V9l4-4 3 3 5-5v10.5z" fill="currentColor" fillOpacity=".3" stroke="none" /><path d="M2 9l4-4 3 3 5-5" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></>,
  circular: <><circle cx="8" cy="8" r="5.8" strokeWidth="1.7" /><path d="M8 2.2V8h5.8" strokeWidth="1.7" /></>,
  dona: <><circle cx="8" cy="8" r="5.8" strokeWidth="1.7" /><circle cx="8" cy="8" r="2.4" strokeWidth="1.7" /></>,
  tabla: <><rect x="2" y="3" width="12" height="10" rx="1.5" strokeWidth="1.6" /><path d="M2 6.5h12M2 9.8h12M6 3v10" strokeWidth="1.4" /></>,
  calor: <><rect x="2" y="2" width="5.5" height="5.5" rx="1" strokeWidth="1.4" /><rect x="8.5" y="2" width="5.5" height="5.5" rx="1" fill="currentColor" stroke="none" /><rect x="2" y="8.5" width="5.5" height="5.5" rx="1" fill="currentColor" stroke="none" /><rect x="8.5" y="8.5" width="5.5" height="5.5" rx="1" strokeWidth="1.4" /></>,
  dispersion: <><circle cx="4" cy="11" r="1.6" fill="currentColor" stroke="none" /><circle cx="7.5" cy="7" r="1.6" fill="currentColor" stroke="none" /><circle cx="11" cy="9" r="1.6" fill="currentColor" stroke="none" /><circle cx="12.5" cy="4" r="1.6" fill="currentColor" stroke="none" /><path d="M2 2v12h12" strokeWidth="1.3" /></>,
  orden: <path d="M5 3v10M2.5 10.5L5 13l2.5-2.5M11 13V3M8.5 5.5L11 3l2.5 2.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />,
  alerta: <><path d="M8 1.5l7 12.5H1z" strokeWidth="1.6" strokeLinejoin="round" /><path d="M8 6v4M8 11.8v.4" strokeWidth="1.8" strokeLinecap="round" /></>,
  ok: <><circle cx="8" cy="8" r="6" strokeWidth="1.6" /><path d="M5 8.2l2 2 4-4.2" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></>,
  info: <><circle cx="8" cy="8" r="6.2" strokeWidth="1.5" /><path d="M8 7.2V11.5M8 4.6v.4" strokeWidth="1.8" strokeLinecap="round" /></>,
  sube: <path d="M8 3l5 7H3z" fill="currentColor" stroke="none" />,
  baja: <path d="M8 13l5-7H3z" fill="currentColor" stroke="none" />,
  igual: <path d="M3 6.5h10M3 9.5h10" strokeWidth="1.8" />,
  fijar: <path d="M6 2h4l-.5 4 2.5 2.5H4L6.5 6zM8 8.5V14" strokeWidth="1.5" strokeLinejoin="round" />,
  guardar: <path d="M3 2h8l2 2v10H3z M5 2v4h6V2 M5 14v-4h6v4" strokeWidth="1.5" strokeLinejoin="round" />,
  exportar: <path d="M8 2v8M4.5 6.5L8 10l3.5-3.5M2.5 12.5v1.5h11v-1.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />,
  chat: <path d="M2 3h12v8H7l-3 3v-3H2z" strokeWidth="1.6" strokeLinejoin="round" />,
  cerrar: <path d="M5 3l5 5-5 5" strokeWidth="1.8" strokeLinecap="round" />,
}

export default function IconoReporte({ nombre, tamano = 16 }) {
  return (
    <svg width={tamano} height={tamano} viewBox="0 0 16 16" fill="none" stroke="currentColor" aria-hidden="true" focusable="false">
      {TRAZOS[nombre]}
    </svg>
  )
}
