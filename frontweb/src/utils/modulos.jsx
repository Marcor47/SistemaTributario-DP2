export const icono = (children) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)

export const MODULOS = [
  {
    ruta: '/predios',
    texto: 'Predios',
    titulo: 'Consulta de predios',
    descripcion: 'Ubica un predio por su código o por el contribuyente asociado y revisa sus características y titulares.',
    icono: icono(<path d="M3 21V9l9-6 9 6v12M9 21v-7h6v7" />),
  },
  {
    ruta: '/contribuyentes',
    texto: 'Contribuyentes',
    titulo: 'Consulta de contribuyentes',
    descripcion: 'Busca por documento o nombre y revisa la ficha, el estado de cuenta y la regularidad de pago.',
    icono: icono(<><circle cx="12" cy="8" r="4" /><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7" /></>),
  },
  {
    ruta: '/metas',
    texto: 'Metas',
    titulo: 'Metas de recaudación',
    descripcion: 'Configura las metas de recaudación de la Gerencia de Rentas y da seguimiento a su cumplimiento.',
    icono: icono(<><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="5" /><circle cx="12" cy="12" r="1" /></>),
  },
  {
    ruta: '/formularios',
    texto: 'Formularios',
    titulo: 'Formularios y plantillas',
    descripcion: 'Descarga y administra las plantillas oficiales de la Municipalidad.',
    icono: icono(<><path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z" /><path d="M14 3v5h5M9 13h6M9 17h6" /></>),
  },
]
