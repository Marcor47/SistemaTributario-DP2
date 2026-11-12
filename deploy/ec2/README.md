# Despliegue en AWS EC2 (Sistema Tributario)

Guía rápida para desplegar la solución completa (Base de datos PostgreSQL, Backend Spring Boot y Frontend React + Nginx) en una instancia EC2 de AWS con IP Elástica.

---

## 1. Requisitos previos en AWS EC2

1. **Instancia EC2** con Ubuntu 22.04 o 24.04 (sirve tanto `t2.micro` como `t3.small` de AWS Academy).
2. **IP Elástica** asociada a tu instancia EC2.
3. **Security Group (Reglas de entrada / Inbound Rules)**:
   - **Puerto 22 (SSH)**: tu IP o `0.0.0.0/0`.
   - **Puerto 80 (HTTP)**: `0.0.0.0/0` (para acceder a la plataforma desde cualquier navegador).
4. Archivo de clave privada `.pem` descargado en tu computadora.

---

## 2. Configuración previa al despliegue (una sola vez)

1. En la carpeta `deploy/ec2/`, copia `deploy.config.example` a `deploy.config`:
   ```bash
   cp deploy/ec2/deploy.config.example deploy/ec2/deploy.config
   ```
   Edita `deploy/ec2/deploy.config`:
   - `EC2_HOST`: Tu IP elástica (ejemplo: `54.160.20.15`).
   - `EC2_KEY_PATH`: La ruta a tu archivo `.pem` (ejemplo: `C:/Users/frank/Downloads/mi-clave.pem`).

2. En la carpeta `deploy/ec2/`, copia `.env.example` a `.env`:
   ```bash
   cp deploy/ec2/.env.example deploy/ec2/.env
   ```
   Edita `deploy/ec2/.env`:
   - Configura una contraseña para `DB_PASSWORD`.
   - Opcionalmente configura un usuario y clave inicial en `INITIAL_ADMIN_USERNAME` y `INITIAL_ADMIN_PASSWORD`.

---

## 3. Ejecutar el despliegue

Abre **Git Bash** o **WSL** en la raíz del proyecto y ejecuta:

```bash
bash deploy/ec2/deploy.sh
```

El script se encargará automáticamente de:
1. Comprobar la conexión SSH con tu clave `.pem`.
2. Empaquetar el proyecto ignorando temporales, node_modules y la app móvil.
3. Subir el paquete a tu servidor EC2.
4. En EC2:
   - Configura 2GB de memoria Swap para asegurar que la compilación nunca falle por falta de memoria.
   - Instala Docker y Docker Compose v2 si aún no están instalados.
   - Construye la imagen de producción de Spring Boot (Java 21) y React (Nginx).
   - Inicia los 3 servicios (`db`, `back`, `frontweb`).
   - Espera a que PostgreSQL y Spring Boot pasen el healthcheck (`/api/health`).
   - Conecta el Frontend y Backend bajo el puerto estándar 80.

---

## 4. Acceso

Una vez terminado, abre en tu navegador:
* **Plataforma Web**: `http://TU_IP_ELASTICA`
* **Salud de la API**: `http://TU_IP_ELASTICA/api/health`

