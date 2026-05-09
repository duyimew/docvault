def call(cfg) {
    echo '>>> Running Trivy Filesystem Scan...'
    echo '>>> Security gate policy: Trivy FS fails on HIGH/CRITICAL findings.'
    sh """
        set -eu

        scan_dir="\$(mktemp -d)"
        cleanup() {
            rm -rf "\$scan_dir"
        }
        trap cleanup EXIT

        tar \\
            --exclude='.git' \\
            --exclude='*/.git' \\
            --exclude='node_modules' \\
            --exclude='*/node_modules' \\
            --exclude='*/node_modules/*' \\
            --exclude='.pnpm-store' \\
            --exclude='*/.pnpm-store' \\
            --exclude='*/.pnpm-store/*' \\
            --exclude='.turbo' \\
            --exclude='*/.turbo' \\
            --exclude='.next' \\
            --exclude='*/.next' \\
            --exclude='dist' \\
            --exclude='*/dist' \\
            --exclude='coverage' \\
            --exclude='*/coverage' \\
            --exclude='dependency-check-report' \\
            --exclude='checkov-report' \\
            --exclude='zap-report' \\
            --exclude='*/.terraform' \\
            --exclude='*/.terraform/*' \\
            -cf - -C '${env.WORKSPACE}' . | tar -xf - -C "\$scan_dir"

        docker run --rm \\
            -v "\$scan_dir:/src:ro" \\
            ${cfg.trivyImage} \\
            fs /src --scanners vuln,secret,misconfig --misconfig-scanners dockerfile,kubernetes,helm --severity HIGH,CRITICAL --exit-code 1 --no-progress
    """
}
