def call() {
    echo '>>> Running SCA Scan...'

    sh '''
        mkdir -p dependency-check-report
        mkdir -p /var/jenkins_home/dependency-check-data
    '''

    catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
        withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
            sh '''
                set -eu

                docker run --rm \\
                    -v "$WORKSPACE:/src" \\
                    -v "$WORKSPACE/dependency-check-report:/report" \\
                    -v /var/jenkins_home/dependency-check-data:/usr/share/dependency-check/data \\
                    owasp/dependency-check:latest \\
                    --project "DocVault" \\
                    --scan /src \\
                    --exclude "**/.agent/**" \\
                    --exclude "**/.agents/**" \\
                    --exclude "**/generated/**" \\
                    --exclude "**/prisma/generated/**" \\
                    --exclude "**/node_modules/**" \\
                    --exclude "**/.pnpm-store/**" \\
                    --exclude "**/.turbo/**" \\
                    --exclude "**/.next/**" \\
                    --exclude "**/dist/**" \\
                    --exclude "**/coverage/**" \\
                    --exclude "**/.scannerwork/**" \\
                    --exclude "**/dependency-check-report/**" \\
                    --exclude "**/checkov-report/**" \\
                    --exclude "**/zap-report/**" \\
                    --format "HTML" \\
                    --format "JSON" \\
                    --out /report \\
                    --failOnCVSS 7 \\
                    --disableKnownExploited \\
                    --nvdApiKey "$NVD_API_KEY"
            '''
        }
    }
}
