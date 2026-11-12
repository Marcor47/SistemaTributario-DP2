import { useSesionInterna } from '../context/sesionInterna.js'
import { etiquetaRol } from '../utils/roles.js'
import './AvisoAccesoReportes.css'

/*
 * HU045 (RF-060): aviso visible en las pantallas de reporte. La aplicación efectiva de las
 * restricciones corresponde al backend; por ahora el aviso comunica el alcance asignado.
 */
export default function AvisoAccesoReportes({ rol }) {
  const { sesion } = useSesionInterna()
  const rolVisible = rol ?? sesion?.rol

  return (
    <aside className="aviso-acceso-reportes" role="note" aria-label="Aviso de acceso a la información">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M12 2 20 6v6c0 5-3.4 8.4-8 10-4.6-1.6-8-5-8-10V6z" />
        <path d="m9 12 2 2 4-4" />
      </svg>
      <div>
        <strong>Información restringida por rol.</strong>{' '}
        Solo se muestra la información asignada {rolVisible ? <>al rol <strong>{etiquetaRol(rolVisible)}</strong></> : 'a tu rol'}.
        Si consideras que falta información o que hay un error, comunícate con el administrador del sistema.
      </div>
    </aside>
  )
}
