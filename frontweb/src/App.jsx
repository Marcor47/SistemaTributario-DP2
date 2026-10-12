import { useEffect, useState } from 'react'

const API_URL = import.meta.env.VITE_API_URL

export default function App() {
  const [estado, setEstado] = useState('Consultando...')

  useEffect(() => {
    fetch(`${API_URL}/health`)
      .then((r) => r.json())
      .then((d) => setEstado(`${d.servicio}: ${d.estado}`))
      .catch(() => setEstado('No se pudo conectar con el back'))
  }, [])

  return (
    <main style={{ padding: 24 }}>
      <h1 style={{ color: 'var(--cabecera)' }}>Plataforma Analítica SAMNET</h1>
      <p>{estado}</p>
    </main>
  )
}