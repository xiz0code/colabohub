# Deploy Frontend ColaboHub en Cloud Run

## Staging

Backend staging actual:

- `https://colabohub-backend-staging-fgoaa2qarq-uc.a.run.app`

### Build de imagen

```powershell
cd frontend

docker build `
  --build-arg VITE_API_BASE_URL=https://colabohub-backend-staging-fgoaa2qarq-uc.a.run.app `
  -t gcr.io/colabohub-staging/colabohub-frontend:staging .
```

### Push

```powershell
docker push gcr.io/colabohub-staging/colabohub-frontend:staging
```

### Deploy

```powershell
& "$env:LOCALAPPDATA\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd" run deploy colabohub-frontend-staging `
  --image gcr.io/colabohub-staging/colabohub-frontend:staging `
  --platform managed `
  --region us-central1 `
  --allow-unauthenticated `
  --memory 512Mi `
  --cpu 1 `
  --min-instances 0 `
  --max-instances 1
```

### Obtener URL

```powershell
& "$env:LOCALAPPDATA\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd" run services describe colabohub-frontend-staging `
  --region us-central1 `
  --format="value(status.url)"
```

## Importante despues del deploy frontend

Una vez que tengas la URL real del frontend staging, debes redeployar backend staging con:

- `FRONTEND_BASE_URL=https://URL_REAL_DEL_FRONTEND`

Para que funcionen bien:

- redireccion de login Google
- CORS
- logout
