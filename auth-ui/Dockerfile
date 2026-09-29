# NPM lock 파일로 공식 JS adapter를 고정한다. 브라우저에는 client secret을 넣지 않는다.
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY index.html app.js ./
RUN mkdir -p public/vendor && cp index.html app.js public/ && cp node_modules/keycloak-js/lib/keycloak.js public/vendor/keycloak.js

FROM nginxinc/nginx-unprivileged:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/public /usr/share/nginx/html
