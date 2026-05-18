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
    String hostCandidates   = sonarHostCandidates(cfg, hostOverride).join(' ')
    String dockerRunArgs    = cfg.sonarDockerRunArgs ?: '--network host --add-host=host.docker.internal:host-gateway'
    String extraArgs        = cfg.extraArgs ?: ''
    boolean enforceQG       = cfg.containsKey('enforceQualityGate') ? cfg.enforceQualityGate : false
    int qgTimeoutMinutes    = (cfg.qualityGateTimeoutMinutes ?: 10) as int

    withSonarQubeEnv(installationName) {
        sh """
            set -eu

            mkdir -p .sonar-cache

            CONFIGURED_SONAR_HOST="${hostOverride}"
            SONAR_HOST=""

            echo ">>> SonarQube installation : ${installationName}"
            echo ">>> Sonar host configured  : \${CONFIGURED_SONAR_HOST}"
            echo ">>> Sonar project key      : ${projectKey}"
            echo ">>> Sonar project name     : ${projectName}"
            echo ">>> Sonar sources          : ${sources}"
            echo ">>> Sonar docker args      : ${dockerRunArgs}"

            for candidate in "\${SONAR_HOST_URL:-}" "\${CONFIGURED_SONAR_HOST}" ${hostCandidates}; do
                if [ -z "\${candidate}" ]; then
                    continue
                fi

                candidate="\${candidate%/}"
                echo ">>> Checking SonarQube reachability: \${candidate}"

                if docker run --rm \\
                    ${dockerRunArgs} \\
                    ${scannerImage} \\
                    sh -c "curl -fsS --connect-timeout 3 --max-time 8 '\${candidate}/api/system/status' >/dev/null"
                then
                    SONAR_HOST="\${candidate}"
                    break
                fi
            done

            if [ -z "\${SONAR_HOST}" ]; then
                echo ">>> ERROR: SonarQube is not reachable from the scanner container."
                echo ">>> Checked candidates: \${SONAR_HOST_URL:-} \${CONFIGURED_SONAR_HOST} ${hostCandidates}"
                echo ">>> Docker containers matching sonar:"
                docker ps --format 'table {{.Names}}\\t{{.Ports}}\\t{{.Status}}' | grep -i sonar || true
                exit 1
            fi

            echo ">>> Sonar host selected    : \${SONAR_HOST}"

            docker run --rm \\
                ${dockerRunArgs} \\
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
        catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
            timeout(time: qgTimeoutMinutes, unit: 'MINUTES') {
                waitForQualityGate abortPipeline: true
            }
        }
    }
}

List sonarHostCandidates(Map cfg, String hostOverride) {
    def candidates = []

    if (cfg.sonarHostCandidates instanceof Collection) {
        candidates.addAll(cfg.sonarHostCandidates.collect { it?.toString()?.trim() }.findAll { it })
    }

    if (hostOverride?.trim()) {
        candidates << hostOverride.trim()
        candidates << hostOverride.trim().replace('host.docker.internal', 'localhost')
        candidates << hostOverride.trim().replace('host.docker.internal', '127.0.0.1')
        candidates << hostOverride.trim().replace('host.docker.internal', '172.17.0.1')
    }

    return candidates.findAll { it }.unique()
}
