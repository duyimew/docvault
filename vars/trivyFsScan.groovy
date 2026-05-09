def call(cfg) {
    echo '>>> Running Trivy Filesystem Scan...'
    echo '>>> Security gate policy: Trivy FS fails on HIGH/CRITICAL findings.'
    sh """
        docker run --rm \\
            -v ${env.WORKSPACE}:/src \\
            ${cfg.trivyImage} \\
            fs /src --scanners vuln,secret,misconfig --severity HIGH,CRITICAL --exit-code 1 --no-progress --skip-dirs .pnpm-store,node_modules
    """
}
