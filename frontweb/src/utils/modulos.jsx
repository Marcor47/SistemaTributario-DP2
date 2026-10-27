export const icono = (children) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

export const MODULOS = [
  {
    ruta: '/estado-cuenta',
    texto: 'Estado de cuenta',
    titulo: 'Mi estado de cuenta',
    descripcion: 'Consulta tus deudas vigentes, pagos realizados y obligaciones tributarias en Servicios en Línea.',
    seccion: 'portal',
    icono: icono(
      <>
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <path d="M3 10h18M7 15h4" />
      </>
    ),
  },
  {
    ruta: '/constancias',
    texto: 'Constancia de No Adeudo',
    titulo: 'Firma de solicitud de constancia',
    descripcion: 'Prepara y firma tu solicitud de Constancia de No Adeudo desde el portal.',
    seccion: 'portal',
    icono: icono(<><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" /><path d="M14 3v6h6M8 15l2 2 5-5" /></>),
  },
  {
    ruta: '/dashboard',
    texto: 'Dashboard',
    titulo: 'Dashboard de Rentas',
    descripcion: 'Revisa las alertas activas por desvío de meta y apoya la toma de decisiones de la Gerencia de Rentas.',
    seccion: 'gestion',
    icono: icono(
      <>
        <rect x="3" y="3" width="7" height="9" rx="1" />
        <rect x="14" y="3" width="7" height="5" rx="1" />
        <rect x="14" y="12" width="7" height="9" rx="1" />
        <rect x="3" y="16" width="7" height="5" rx="1" />
      </>
    ),
  },
  {
    ruta: '/predios',
    texto: 'Predios',
    titulo: 'Consulta de predios',
    descripcion: 'Ubica un predio por su código o por el contribuyente asociado y revisa sus características y titulares.',
    seccion: 'gestion',
    icono: icono(<path d="M3 21V9l9-6 9 6v12M9 21v-7h6v7" />),
  },
  {
    ruta: '/contribuyentes',
    texto: 'Contribuyentes',
    titulo: 'Consulta de contribuyentes',
    descripcion: 'Busca por documento o nombre y revisa la ficha, el estado de cuenta y la regularidad de pago.',
    seccion: 'gestion',
    icono: icono(<><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7" /></>),
  },
  {
    ruta: '/metas',
    texto: 'Metas',
    titulo: 'Metas de recaudación',
    descripcion: 'Configura las metas de recaudación de la Gerencia de Rentas y da seguimiento a su cumplimiento.',
    seccion: 'gestion',
    icono: icono(<><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="5" /><circle cx="12" cy="12" r="1" /></>),
  },
  {
    ruta: '/formularios',
    texto: 'Formularios',
    titulo: 'Formularios y plantillas',
    descripcion: 'Descarga y administra las plantillas oficiales de la Municipalidad.',
    seccion: 'gestion',
    icono: icono(<><path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z" /><path d="M14 3v5h5M9 13h6M9 17h6" /></>),
  },
  {
    ruta: '/morosidad',
    texto: 'Morosidad',
    titulo: 'Índice de Morosidad',
    descripcion: 'Consulta el índice total, por tributo y por zona de cada periodo publicado para priorizar la gestión de cobranza.',
    seccion: 'gestion',
    icono: icono(<><path d="M3 3v18h18" /><path d="M18 8l-6 6-3-3-5 5" /></>),
  },
]
