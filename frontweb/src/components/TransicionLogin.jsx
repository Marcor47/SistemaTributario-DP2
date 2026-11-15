import { useCallback, useMemo, useState } from 'react'
import { TransicionLoginContext } from '../context/transicionLogin.js'
import '../pages/LoginInstitucional.css'

/** La capa permanece montada entre rutas; la navegación no espera a la animación. */
export default function TransicionLoginProvider({ children }) {
  const [activa, setActiva] = useState(false)
  const iniciar = useCallback(() => setActiva(true), [])
  const valor = useMemo(() => ({ activa, iniciar }), [activa, iniciar])

  return <TransicionLoginContext.Provider value={valor}>
    {children}
    {activa && <div className="li-transicion" aria-hidden="true"
      onAnimationEnd={(evento) => { if (evento.target === evento.currentTarget) setActiva(false) }} />}
  </TransicionLoginContext.Provider>
}
