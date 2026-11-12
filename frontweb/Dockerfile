# Etapa 1: Compilacion de React con Node.js y Vite
FROM node:20-alpine AS builder
WORKDIR /app

# Copiar definicion de dependencias
COPY package*.json ./
RUN npm ci

# Copiar codigo fuente
COPY . .

# Fijar VITE_API_URL en ruta relativa /api para consumir mediante Nginx reverse proxy
ENV VITE_API_URL=/api
RUN npm run build

# Etapa 2: Servidor web de produccion con Nginx Alpine
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Copiar bundle de produccion generado por Vite
COPY --from=builder /app/dist .

# Copiar configuracion personalizada de Nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

