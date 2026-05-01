def call() {
    echo '>>> Running SCA Scan...'

    sh '''
        mkdir -p dependency-check-report
        mkdir -p /var/jenkins_home/dependency-check-data
    '''

    catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
        withCredentials([string(credentialsId: 'nvd-api-key', variable: 'NVD_API_KEY')]) {
            sh """
                docker run --rm \\
                    -v ${env.WORKSPACE}:/src \\
                    -v ${env.WORKSPACE}/dependency-check-report:/report \\
                    -v /var/jenkins_home/dependency-check-data:/usr/share/dependency-check/data \\
                    owasp/dependency-check:latest \\
                    --project "DocVault" \\
                    --scan /src \\
                    --exclude "**/node_modules/**" \\
                    --exclude "**/.pnpm-store/**" \\
                    --format "HTML" \\
                    --format "JSON" \\
                    --out /report \\
                    --failOnCVSS 7 \\
                    --disableKnownExploited \\
                    --nvdApiKey "$NVD_API_KEY"
            """
        }
    }
}