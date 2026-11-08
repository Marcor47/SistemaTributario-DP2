import { useRef } from 'react'
import { Reorder, useDragControls } from 'framer-motion'

function GrupoColumnas({ grupo, columnasVisibles, etiquetas, arrastre, onAlternar, onAlternarGrupo, onReordenar }) {
  const controles = useDragControls()
  const arrastreGrupo = useRef(false)
  const visibles = grupo.columnas.filter((columna) => columnasVisibles.includes(columna)).length
  const todasVisibles = visibles === grupo.columnas.length
  const estado = visibles === 0 ? 'ninguna' : todasVisibles ? 'todas' : 'parcial'
  return (
    <Reorder.Item
      as="section"
      value={grupo.clave}
      className="reporte-columnas-bloque"
      aria-label={`Grupo ${grupo.etiqueta}`}
      dragListener={false}
      dragControls={controles}
      onDragStart={() => { arrastreGrupo.current = true }}
      onDragEnd={() => { window.setTimeout(() => { arrastreGrupo.current = false }, 0) }}
    >
      <button
        type="button"
        className="reporte-columnas-bloque__cabecera"
        title={`${todasVisibles ? 'Ocultar' : 'Mostrar'} todas las columnas del grupo. Arrastra para moverlo.`}
        aria-pressed={todasVisibles}
        data-estado={estado}
        onPointerDown={(evento) => controles.start(evento)}
        onClick={() => {
          if (arrastreGrupo.current) return
          onAlternarGrupo(grupo.clave)
        }}
      >
        {grupo.etiqueta}
      </button>
      <Reorder.Group
        as="div"
        axis="x"
        values={grupo.columnas}
        onReorder={(columnas) => onReordenar(grupo.clave, columnas)}
        className="reporte-columnas-bloque__columnas"
        role="group"
        aria-label={`Columnas del grupo ${grupo.etiqueta}`}
      >
        {grupo.columnas.map((columna) => (
          <Reorder.Item
            as="button"
            key={columna}
            value={columna}
            type="button"
            className="reporte-segmento"
            aria-pressed={columnasVisibles.includes(columna)}
            onDragStart={() => { arrastre.current = true }}
            onDragEnd={() => { window.setTimeout(() => { arrastre.current = false }, 0) }}
            onClick={() => {
              if (arrastre.current) return
              onAlternar(columna)
            }}
          >
            {etiquetas[columna]}
          </Reorder.Item>
        ))}
      </Reorder.Group>
    </Reorder.Item>
  )
}

export default function SelectorColumnasAgrupadas({
  grupos, columnasVisibles, etiquetas, arrastre, onAlternar, onAlternarGrupo, onReordenarGrupos, onReordenarColumnas,
  onScroll, ariaLabel,
}) {
  return (
    <Reorder.Group
      as="div"
      axis="x"
      values={grupos.map((grupo) => grupo.clave)}
      onReorder={onReordenarGrupos}
      onScroll={onScroll}
      className="reporte-columnas-grid reporte-columnas-grid--grupos"
      role="group"
      aria-label={ariaLabel}
    >
      {grupos.map((grupo) => (
        <GrupoColumnas
          key={grupo.clave}
          grupo={grupo}
          columnasVisibles={columnasVisibles}
          etiquetas={etiquetas}
          arrastre={arrastre}
          onAlternar={onAlternar}
          onAlternarGrupo={onAlternarGrupo}
          onReordenar={onReordenarColumnas}
        />
      ))}
    </Reorder.Group>
  )
}
