def call(cfg) {
    echo '>>> Running Trivy Filesystem Scan...'
    sh """
        docker run --rm \\
            -v ${env.WORKSPACE}:/src \\
            ${cfg.trivyImage} \\
            fs /src --scanners vuln,secret,misconfig --severity CRITICAL --no-progress --skip-dirs .pnpm-store,node_modules
    """
}
