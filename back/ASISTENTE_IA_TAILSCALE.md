# Conectar el backend al servicio de IA por Tailscale

- El modelo solo genera texto: no ve la base de datos ni credenciales (ver [REPORTERIA_API.md](REPORTERIA_API.md)).
- El servicio **se enciende a pedido** porque ocupa la GPU. Si está apagado, el panel muestra «Servicio de IA no
  disponible» y el resto de la plataforma funciona igual.

## Que se necesita
1. Una **clave de acceso de Tailscale** (`tskey-auth-…`) con la etiqueta `tag:muni-back`.
2. El **token del servicio** (`LLM_TOKEN`, 64 caracteres hexadecimales).

## Pasos Backend

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

El dispositivo entra a la red  como «dispositivo etiquetado» (`tag:muni-back`), no como tu usuario. Si ya usas
Tailscale con tu propia red, este comando te cambia a la red de IA; para volver, `tailscale logout` y vuelve a entrar con
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

Puede llamar a `100.122.1.26:8095` y nada más: la política de la red bloquea los demás puertos, el servicio corta preguntas de más de 6000 tokens o 256 KB.
