def call(Map cfg = [:]) {
    echo '>>> Running SAST Scan (SonarQube)...'

    String installationName = cfg.sonarQubeInstallation ?: 'sqdocvault'
    String scannerImage     = cfg.sonarScannerImage ?: 'sonarsource/sonar-scanner-cli:latest'
    String projectKey       = cfg.sonarProjectKey ?: 'docvault'
    String projectName      = cfg.sonarProjectName ?: 'DocVault'
    String projectVersion   = cfg.sonarProjectVersion ?: (env.BUILD_NUMBER ?: 'local')
    String sources          = cfg.sonarSources ?: 'apps,services,libs'
    String exclusions       = cfg.sonarExclusions ?: '**/node_modules/**,**/.pnpm-store/**,**/dist/**,**/.next/**,**/coverage/**,infra/**,charts/**,checkov-report/**,dependency-check-report/**,Dockerfile.jenkins,**/.scannerwork/**'
    String hostOverride     = cfg.sonarHostUrl ?: 'http://sonarqube:9000'
    String extraArgs        = cfg.extraArgs ?: ''
    boolean enforceQG       = cfg.containsKey('enforceQualityGate') ? cfg.enforceQualityGate : false
    int qgTimeoutMinutes    = (cfg.qualityGateTimeoutMinutes ?: 10) as int

    withSonarQubeEnv(installationName) {
        sh """
            set -eu

            mkdir -p .sonar-cache

            SONAR_HOST="${hostOverride}"

            echo ">>> SonarQube installation : ${installationName}"
            echo ">>> Sonar host             : \${SONAR_HOST}"
            echo ">>> Sonar project key      : ${projectKey}"
            echo ">>> Sonar project name     : ${projectName}"
            echo ">>> Sonar sources          : ${sources}"

            docker run --rm \\
                --network host \\
                -v "${env.WORKSPACE}:/usr/src" \\
                -v "${env.WORKSPACE}/.sonar-cache:/opt/sonar-scanner/.sonar/cache" \\
                -w /usr/src \\
                -e SONAR_HOST_URL="\${SONAR_HOST}" \\
                -e SONAR_TOKEN="${env.SONAR_AUTH_TOKEN}" \\
                ${scannerImage} \\
                -Dsonar.projectKey="${projectKey}" \\
                -Dsonar.projectName="${projectName}" \\
                -Dsonar.projectVersion="${projectVersion}" \\
                -Dsonar.sources="${sources}" \\
                -Dsonar.exclusions="${exclusions}" \\
                -Dsonar.host.url="\${SONAR_HOST}" \\
                -Dsonar.scanner.skipJreProvisioning=true \\
                ${extraArgs}
        """
    }

    if (enforceQG) {
        echo '>>> Waiting for SonarQube Quality Gate...'
        timeout(time: qgTimeoutMinutes, unit: 'MINUTES') {
            waitForQualityGate abortPipeline: true
        }
    }
}