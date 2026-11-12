# Conectar el backend al servicio de IA por Tailscale

El Asistente IA de la plataforma analítica usa un modelo de lenguaje (Llama 3.1 8B) que corre en la máquina con GPU de
Gyths (`gyts-1`). Esa máquina **no está publicada en internet ni en su red local**: solo se llega por Tailscale, y la
red solo deja entrar al **puerto 8095** (el servicio de IA). Ningún otro puerto ni servicio de esa máquina es accesible.

```
backend (tu máquina o el servidor) ── Tailscale, dispositivo con etiqueta tag:muni-back ──► 100.122.1.26:8095
                                       con LLM_TOKEN                                         (servicio de IA)
```

- El modelo solo genera texto: no ve la base de datos ni credenciales (ver [REPORTERIA_API.md](REPORTERIA_API.md)).
- El servicio **se enciende a pedido** porque ocupa la GPU. Si está apagado, el panel muestra «Servicio de IA no
  disponible» y el resto de la plataforma funciona igual. Pídele a Gyths que lo encienda cuando lo necesites.

## Lo que te pasa Gyths (por mensaje privado, nunca en el repositorio ni en el chat del grupo)

1. Una **clave de acceso de Tailscale** (`tskey-auth-…`) con la etiqueta `tag:muni-back`.
2. El **token del servicio** (`LLM_TOKEN`, 64 caracteres hexadecimales).

## Pasos (en la máquina donde corre el backend)

### 1. Unirse a la red con la clave

Linux / macOS:

```bash
curl -fsSL https://tailscale.com/install.sh | sh          # en macOS: instalar la app de tailscale.com/download
sudo tailscale up --auth-key <clave tskey-auth-…> --hostname muni-back-<tu-nombre>
```

Windows: instalar desde <https://tailscale.com/download> y, en PowerShell como administrador:

```powershell
tailscale up --auth-key <clave tskey-auth-…> --hostname muni-back-<tu-nombre>
```

El dispositivo entra a la red de Gyths como «dispositivo etiquetado» (`tag:muni-back`), no como tu usuario. Si ya usas
Tailscale con tu propia red, este comando te cambia a la de Gyths; para volver, `tailscale logout` y vuelve a entrar con
tu cuenta (o usa el cambio rápido de cuenta de la app).

### 2. Configurar el backend

Las dos variables, como variables de entorno o, en el perfil `local`, en `back/.asistente-local.properties` (copiar de
`.asistente-local.properties.example`; Git lo ignora):

```properties
LLM_URL=http://100.122.1.26:8095
LLM_TOKEN=<token de 64 caracteres>
```

- **Nada después del valor en la misma línea**: ni espacios ni comentarios `# …`. En archivos `.properties` y `.env`
  todo lo que sigue al `=` es parte del valor y el servicio responde «token inválido».
- Usa la **IP**, no el nombre `gyts-1`: dentro de contenedores el nombre no se resuelve.

### 3. Comprobar

```bash
curl http://100.122.1.26:8095/salud
```

| Respuesta | Qué significa |
| --- | --- |
| `{"estado": "listo", …}` | Todo bien. Reinicia el backend y prueba el interruptor «Asistente IA» de la plataforma analítica. |
| `{"estado": "cargando", …}` | Se está encendiendo (10–30 s). |
| Se queda esperando y falla | El servicio está apagado, tu dispositivo no está en la red (`tailscale status`) o la clave no tenía la etiqueta. |

Si `/salud` responde pero el asistente dice «El servicio de IA rechazó la consulta», el token está mal copiado (ver el
punto sobre comentarios y espacios).

## Qué puede y qué no puede hacer tu dispositivo en esa red

Puede llamar a `100.122.1.26:8095` y nada más: la política de la red bloquea los demás puertos de `gyts-1` y el resto
de dispositivos. Un pedido a la vez usa la GPU; el servicio corta preguntas de más de 6000 tokens o 256 KB.

## Para Gyths (dueño de la red y de la GPU)

- Encender y apagar: `poc/muni-lakehouse/servicio-llm/llm.sh iniciar | detener | estado | logs`. El servicio escucha
  solo en la IP de Tailscale (`LLM_HOST=100.122.1.26` en `llm.env`).
- Política de la red (consola de Tailscale → Access controls): sin la regla por defecto «todos a todo»; tus
  dispositivos acceden a todo y `tag:muni-back` solo a `gpu-llm:8095`, con una sección `tests` que lo verifica al
  guardar.
- Claves: Settings → Keys → Generate auth key, reutilizable, preaprobada, etiqueta `tag:muni-back`, con vencimiento.
  Una clave filtrada solo permite llegar al puerto 8095 y aún pide el token; se revoca en la misma pantalla.
- Prueba de la política: `poc/muni-lakehouse/servicio-llm/probar_acceso.sh ~/.config/muni-ts/authkey.env` levanta un
  dispositivo de prueba con la etiqueta y comprueba que el 8095 responde y que un puerto en uso (8000) está bloqueado.
  Después, borrar el dispositivo `muni-back-prueba*` en la consola (Machines → … → Remove).
- Cambiar el token: generar otro (`openssl rand -hex 32`), ponerlo en `llm.env`, reiniciar el servicio y pasárselo
  al equipo.
