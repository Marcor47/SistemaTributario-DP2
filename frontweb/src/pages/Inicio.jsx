import { Link } from 'react-router-dom'
import { MotionConfig, motion } from 'framer-motion'
import './Inicio.css'

const MODULOS = [
  {
    titulo: 'Consulta de predios',
    imagen: '/modulos/predios.jpg',
    texto: 'Ubica un predio por código y revisa su ficha, titulares y estado de cuenta tributaria.',
    icono: <path d="M3 21V9l9-6 9 6v12M9 21v-7h6v7" />,
  },
  {
    titulo: 'Contribuyentes',
    imagen: '/modulos/contribuyentes.jpg',
    texto: 'Consulta por documento o nombre los datos del contribuyente y sus predios asociados.',
    icono: (
      <>
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21c0-4 3.6-7 8-7s8 3 8 7" />
      </>
    ),
  },
  {
    titulo: 'Regularidad de pago',
    imagen: '/modulos/regularidad.jpg',
    texto: 'Identifica el comportamiento de pago y la línea de tiempo de cobranza de cada cuenta.',
    icono: (
      <>
        <path d="M3 3v18h18" />
        <path d="M7 15l4-4 3 3 5-6" />
      </>
    ),
  },
  {
    titulo: 'Metas de recaudación',
    imagen: '/modulos/metas.jpg',
    texto: 'Configura y da seguimiento a las metas de recaudación de la Gerencia de Rentas.',
    icono: (
      <>
        <circle cx="12" cy="12" r="9" />
        <circle cx="12" cy="12" r="5" />
        <circle cx="12" cy="12" r="1" />
      </>
    ),
  },
  {
    titulo: 'Formularios y plantillas',
    imagen: '/modulos/formularios.jpg',
    texto: 'Descarga las plantillas oficiales: declaración jurada, constancia de no adeudo y más.',
    icono: (
      <>
        <path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z" />
        <path d="M14 3v5h5M9 13h6M9 17h6" />
      </>
    ),
  },
  {
    titulo: 'Estado de cuenta',
    imagen: '/modulos/estado-cuenta.jpg',
    texto: 'Revisa deudas, pagos y cobranza pendiente por tributo y periodo en un solo lugar.',
    icono: (
      <>
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <path d="M3 10h18M7 15h4" />
      </>
    ),
  },
]

const PASOS = [
  { n: '1', titulo: 'Ingresa con tu cuenta', texto: 'Accede con las credenciales asignadas por la Municipalidad.' },
  { n: '2', titulo: 'Elige un módulo', texto: 'Consulta predios, contribuyentes, metas o formularios.' },
  { n: '3', titulo: 'Gestiona con datos claros', texto: 'Obtén la información tributaria que necesitas, sin trámites adicionales.' },
]

const aparece = {
  initial: { opacity: 0, y: 24 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, margin: '-60px' },
  transition: { duration: 0.5, ease: 'easeOut' },
}

export default function Inicio() {
  return (
    <MotionConfig reducedMotion="user">
      <div className="inicio">
        <a className="in-skip" href="#modulos">Saltar al contenido</a>

        <header className="in-top">
          <Link to="/" className="in-top__logo" aria-label="Municipalidad de San Miguel, inicio">
            <img src="/logo-san-miguel.png" alt="Municipalidad San Miguel" width="178" height="74" />
          </Link>
          <nav className="in-top__nav" aria-label="Secciones">
            <a href="#modulos">Módulos</a>
            <a href="#como-funciona">Cómo funciona</a>
          </nav>
          <Link to="/login" className="in-btn in-btn--primario">Ingresar</Link>
        </header>

        <main>
          <section className="in-hero">
            <div className="in-hero__fondo" aria-hidden="true" />
            <div className="in-hero__contenido">
              <motion.h1
                initial={{ opacity: 0, y: 24 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.55 }}
              >
                Gestión tributaria al servicio de <span>San Miguel</span>
              </motion.h1>
              <motion.p
                className="in-hero__texto"
                initial={{ opacity: 0, y: 24 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.55, delay: 0.1 }}
              >
                Consulta predios y contribuyentes, da seguimiento a la cobranza y administra las metas de
                recaudación desde una plataforma única, clara y segura.
              </motion.p>
              <motion.div
                className="in-hero__acciones"
                initial={{ opacity: 0, y: 24 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.55, delay: 0.2 }}
              >
                <Link to="/login" className="in-btn in-btn--cian in-btn--grande">
                  Ingresar al sistema
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M5 12h14M13 6l6 6-6 6" />
                  </svg>
                </Link>
                <a href="#modulos" className="in-btn in-btn--contorno in-btn--grande">Conocer los módulos</a>
              </motion.div>
              <motion.ul
                className="in-hero__puntos"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 0.6, delay: 0.4 }}
              >
                {['Información centralizada', 'Acceso institucional', 'Plantillas oficiales'].map((t) => (
                  <li key={t}>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M5 12.5l4.5 4.5L19 7.5" />
                    </svg>
                    {t}
                  </li>
                ))}
              </motion.ul>
            </div>

            <motion.figure
              className="in-hero__foto"
              initial={{ opacity: 0, x: 40 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.7, delay: 0.2, ease: 'easeOut' }}
            >
              <img
                src="/municipalidad.jpg"
                alt="Fachada del edificio de la Municipalidad de San Miguel"
                width="447"
                height="447"
              />
              <motion.span
                className="in-hero__sello"
                aria-hidden="true"
                animate={{ y: [0, -8, 0] }}
                transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}
              >
                <img src="/logo-san-miguel-circulo.png" alt="" width="342" height="342" />
              </motion.span>
            </motion.figure>
          </section>

          <section id="modulos" className="in-seccion">
            <motion.div className="in-seccion__cab" {...aparece}>
              <h2>Todo lo que necesitas para la gestión tributaria</h2>
              <p>Módulos pensados para el trabajo diario de la Gerencia de Rentas.</p>
            </motion.div>
            <ul className="in-grid">
              {MODULOS.map((m, i) => (
                <motion.li
                  key={m.titulo}
                  className="in-card"
                  initial={{ opacity: 0, y: 24 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true, margin: '-40px' }}
                  transition={{ duration: 0.45, delay: (i % 3) * 0.08 }}
                  whileHover={{ y: -4 }}
                >
                  <div className="in-card__media">
                    <img src={m.imagen} alt="" width="900" height="502" loading="lazy" />
                  </div>
                  <div className="in-card__cuerpo">
                    <span className="in-card__icono">
                      <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        {m.icono}
                      </svg>
                    </span>
                    <h3>{m.titulo}</h3>
                    <p>{m.texto}</p>
                  </div>
                </motion.li>
              ))}
            </ul>
          </section>

          <section id="como-funciona" className="in-seccion in-seccion--tenue">
            <motion.div className="in-seccion__cab" {...aparece}>
              <h2>Cómo funciona</h2>
              <p>Tres pasos para empezar a trabajar.</p>
            </motion.div>
            <ol className="in-pasos">
              {PASOS.map((p, i) => (
                <motion.li
                  key={p.n}
                  className="in-paso"
                  initial={{ opacity: 0, y: 24 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true, margin: '-40px' }}
                  transition={{ duration: 0.45, delay: i * 0.1 }}
                >
                  <span className="in-paso__n" aria-hidden="true">{p.n}</span>
                  <h3>{p.titulo}</h3>
                  <p>{p.texto}</p>
                </motion.li>
              ))}
            </ol>
          </section>

          <section className="in-cta">
            <motion.div {...aparece}>
              <h2>¿Listo para comenzar?</h2>
              <p>Ingresa con tu cuenta institucional para acceder al sistema.</p>
              <Link to="/login" className="in-btn in-btn--claro in-btn--grande">Ingresar al sistema</Link>
            </motion.div>
          </section>
        </main>

        <footer className="in-pie">
          <img src="/logo-san-miguel.png" alt="Municipalidad San Miguel" width="142" height="59" />
          <p>
            Gerencia de Rentas · Municipalidad de San Miguel. Los datos mostrados en la demostración son
            ficticios.
          </p>
        </footer>
      </div>
    </MotionConfig>
  )
}
