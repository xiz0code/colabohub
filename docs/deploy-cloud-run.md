# Deploy ColaboHub Backend en Cloud Run

Esta guia deja dos ambientes independientes:

- `colabohub-backend-staging`
- `colabohub-backend-prod`

## 1. Requisitos

- Proyecto GCP creado
- `gcloud` instalado y autenticado
- Docker instalado
- Artifact Registry o Container Registry habilitado

## 2. Variables base

Reemplaza estos valores en los comandos:

```powershell
$env:PROJECT_ID="TU_PROJECT_ID"
$env:REGION="us-central1"
$env:STAGING_SERVICE="colabohub-backend-staging"
$env:PROD_SERVICE="colabohub-backend-prod"
$env:IMAGE_NAME="colabohub-backend"
```

## 3. Build del jar

Desde la carpeta `backend`:

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd' clean package -DskipTests
```

## 4. Build de imagen Docker

```powershell
cd backend
docker build -t gcr.io/$env:PROJECT_ID/$env:IMAGE_NAME .
```

## 5. Auth Docker con gcloud

```powershell
gcloud auth configure-docker
```

## 6. Push de imagen

```powershell
docker push gcr.io/$env:PROJECT_ID/$env:IMAGE_NAME
```

## 7. Archivo de variables

Usa estos templates:

- `backend/cloudrun.staging.env.example`
- `backend/cloudrun.prod.env.example`

Duplicalos como:

- `backend/cloudrun.staging.env`
- `backend/cloudrun.prod.env`

Y completa credenciales reales.

## 8. Deploy STAGING

```powershell
gcloud run deploy $env:STAGING_SERVICE `
  --image gcr.io/$env:PROJECT_ID/$env:IMAGE_NAME `
  --platform managed `
  --region $env:REGION `
  --allow-unauthenticated `
  --memory 512Mi `
  --cpu 1 `
  --min-instances 0 `
  --max-instances 1 `
  --env-vars-file cloudrun.staging.env
```

## 9. Deploy PRODUCCION

```powershell
gcloud run deploy $env:PROD_SERVICE `
  --image gcr.io/$env:PROJECT_ID/$env:IMAGE_NAME `
  --platform managed `
  --region $env:REGION `
  --allow-unauthenticated `
  --memory 1Gi `
  --cpu 1 `
  --min-instances 0 `
  --max-instances 2 `
  --env-vars-file cloudrun.prod.env
```

## 10. Perfil Spring por ambiente

- `application-staging.yml`
  - logs mas detallados
  - correos desactivados por `APP_MAIL_ENABLED=false`
  - base de datos staging
- `application-prod.yml`
  - logs mas optimizados
  - correos activos
  - base de datos real

## 11. Validacion post deploy

Obtener URL:

```powershell
gcloud run services describe $env:STAGING_SERVICE --region $env:REGION --format="value(status.url)"
gcloud run services describe $env:PROD_SERVICE --region $env:REGION --format="value(status.url)"
```

Probar salud:

```powershell
curl https://URL_DEL_SERVICIO/health
curl https://URL_DEL_SERVICIO/actuator/health
```

Probar endpoint protegido:

```powershell
curl -I https://URL_DEL_SERVICIO/api/me
```

Respuesta esperada sin sesion:

- `401 Unauthorized`

## 12. Recomendaciones antes de produccion

- usar Cloud SQL PostgreSQL
- guardar secretos sensibles en Secret Manager
- configurar dominio o frontend base real por ambiente
- probar login Google real en `staging`
- probar cierre diario y envio de correos en `staging`
