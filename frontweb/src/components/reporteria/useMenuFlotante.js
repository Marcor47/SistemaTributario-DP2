import { useEffect, useState } from 'react'

/* Menú desplegable: se cierra al hacer clic fuera o con Escape. */
export default function useMenuFlotante(contenedor) {
  const [abierto, setAbierto] = useState(false)
  useEffect(() => {
    if (!abierto) return undefined
    const alClic = (evento) => { if (!contenedor.current?.contains(evento.target)) setAbierto(false) }
    const alTecla = (evento) => {
      if (evento.key === 'Escape') {
        evento.stopPropagation()
        setAbierto(false)
      }
    }
    document.addEventListener('click', alClic)
    document.addEventListener('keydown', alTecla, true)
    return () => {
      document.removeEventListener('click', alClic)
      document.removeEventListener('keydown', alTecla, true)
    }
  }, [abierto, contenedor])
  return { abierto, setAbierto }
}
