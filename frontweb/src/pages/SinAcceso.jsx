import { Link } from 'react-router-dom'

export default function SinAcceso() {
  return <section className="pcard"><h2>No tienes acceso a este módulo</h2><p className="hint">Tu cuenta está activa, pero el rol asignado no incluye esta función.</p><p><Link className="btn" to="/">Volver al inicio</Link></p></section>
}
