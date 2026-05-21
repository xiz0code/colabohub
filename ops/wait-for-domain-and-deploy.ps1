$ErrorActionPreference = "Stop"

$repoRoot = "C:\Users\efrei\OneDrive\Documentos\Dev\ColaborApp"
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"
$logDir = Join-Path $repoRoot "ops\logs"
$lockFile = Join-Path $logDir "domain-deploy.lock"
$logFile = Join-Path $logDir "domain-deploy.log"
$successFile = Join-Path $logDir "domain-deploy.success.txt"
$errorFile = Join-Path $logDir "domain-deploy.error.txt"
$taskName = "ColaboHubDomainDeploy"

New-Item -ItemType Directory -Path $logDir -Force | Out-Null

if (Test-Path $lockFile) {
    $stamp = Get-Content $lockFile -ErrorAction SilentlyContinue
    Add-Content -Path $logFile -Value "[$(Get-Date -Format s)] Otra ejecucion sigue activa ($stamp). Se omite esta corrida."
    exit 0
}

Set-Content -Path $lockFile -Value "$(Get-Date -Format s)"

function Write-Log {
    param([string]$Message)
    Add-Content -Path $logFile -Value "[$(Get-Date -Format s)] $Message"
}

function Notify-User {
    param(
        [string]$Title,
        [string]$Message
    )

    try {
        msg $env:USERNAME "$Title`n$Message" | Out-Null
    }
    catch {
        Write-Log "No se pudo enviar notificacion visual con msg.exe."
    }
}

function Get-GcloudPath {
    return Join-Path $env:LOCALAPPDATA "Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
}

function Get-MavenPath {
    return "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd"
}

function Get-DomainStatus {
    param([string]$Domain)

    $gcloud = Get-GcloudPath
    $json = & $gcloud beta run domain-mappings describe --domain $Domain --region us-central1 --format=json 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo consultar el dominio $Domain. Salida: $json"
    }

    $data = $json | ConvertFrom-Json
    $ready = ($data.status.conditions | Where-Object { $_.type -eq "Ready" } | Select-Object -First 1).status
    $certificate = ($data.status.conditions | Where-Object { $_.type -eq "CertificateProvisioned" } | Select-Object -First 1).status

    [PSCustomObject]@{
        Domain = $Domain
        Ready = $ready
        CertificateProvisioned = $certificate
    }
}

function Deploy-Backend {
    $gcloud = Get-GcloudPath
    $maven = Get-MavenPath

    Write-Log "Iniciando deploy backend."
    Push-Location $backendDir
    try {
        & $gcloud config set project colabohub | Out-Null
        $env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\jbr"
        & $maven clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Fallo Maven backend." }

        docker build -t gcr.io/colabohub/colabohub-backend:prod .
        if ($LASTEXITCODE -ne 0) { throw "Fallo docker build backend." }

        docker push gcr.io/colabohub/colabohub-backend:prod
        if ($LASTEXITCODE -ne 0) { throw "Fallo docker push backend." }

        & $gcloud run deploy colabohub-backend-prod --image gcr.io/colabohub/colabohub-backend:prod --platform managed --region us-central1 --allow-unauthenticated --memory 1Gi --cpu 1 --min-instances 0 --max-instances 2 --env-vars-file cloudrun.prod.env
        if ($LASTEXITCODE -ne 0) { throw "Fallo deploy backend." }
    }
    finally {
        Pop-Location
    }
}

function Deploy-Frontend {
    $gcloud = Get-GcloudPath

    Write-Log "Iniciando deploy frontend."
    Push-Location $frontendDir
    try {
        & $gcloud config set project colabohub | Out-Null

        docker build --build-arg VITE_API_BASE_URL=https://api.colabohub.cl -t gcr.io/colabohub/colabohub-frontend:prod .
        if ($LASTEXITCODE -ne 0) { throw "Fallo docker build frontend." }

        docker push gcr.io/colabohub/colabohub-frontend:prod
        if ($LASTEXITCODE -ne 0) { throw "Fallo docker push frontend." }

        & $gcloud run deploy colabohub-frontend-prod --image gcr.io/colabohub/colabohub-frontend:prod --platform managed --region us-central1 --allow-unauthenticated --memory 512Mi --cpu 1 --min-instances 0 --max-instances 2
        if ($LASTEXITCODE -ne 0) { throw "Fallo deploy frontend." }
    }
    finally {
        Pop-Location
    }
}

function Remove-SelfTask {
    Write-Log "Eliminando tarea programada $taskName."
    schtasks /Delete /TN $taskName /F | Out-Null
}

try {
    Write-Log "Revisando estado de dominios."
    $frontendStatus = Get-DomainStatus -Domain "colabohub.cl"
    $apiStatus = Get-DomainStatus -Domain "api.colabohub.cl"

    Write-Log "Estado colabohub.cl Ready=$($frontendStatus.Ready) Cert=$($frontendStatus.CertificateProvisioned)"
    Write-Log "Estado api.colabohub.cl Ready=$($apiStatus.Ready) Cert=$($apiStatus.CertificateProvisioned)"

    $isReady =
        $frontendStatus.Ready -eq "True" -and
        $frontendStatus.CertificateProvisioned -eq "True" -and
        $apiStatus.Ready -eq "True" -and
        $apiStatus.CertificateProvisioned -eq "True"

    if (-not $isReady) {
        Write-Log "Los dominios aun no estan listos. Se reintentara en la siguiente ejecucion."
        exit 0
    }

    Write-Log "Dominios listos. Ejecutando deploy final con dominio."
    Deploy-Backend
    Deploy-Frontend
    Write-Log "Deploy terminado correctamente."
    Set-Content -Path $successFile -Value "[$(Get-Date -Format s)] Dominio listo y deploy completado."
    Remove-Item -Path $errorFile -ErrorAction SilentlyContinue
    Notify-User -Title "ColaboHub listo" -Message "El dominio colabohub.cl ya quedo operativo y el deploy final termino bien."
    Remove-SelfTask
}
catch {
    Write-Log "ERROR: $($_.Exception.Message)"
    Set-Content -Path $errorFile -Value "[$(Get-Date -Format s)] ERROR: $($_.Exception.Message)"
    Notify-User -Title "ColaboHub error" -Message "La automatizacion detecto un error. Revisa ops\\logs\\domain-deploy.log."
    exit 1
}
finally {
    Remove-Item -Path $lockFile -ErrorAction SilentlyContinue
}
