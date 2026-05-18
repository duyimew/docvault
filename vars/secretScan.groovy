def call() {
    echo '>>> Running Secret Scan (TruffleHog)...'
    sh """
        set -eu
        mkdir -p secret-scan-report

        echo ">>> Scanning repository for leaked secrets..."

        # Run TruffleHog filesystem scan
        # --fail: Exit with code 1 if secrets are found
        docker run --rm \
            -v ${env.WORKSPACE}:/workspace \
            trufflesecurity/trufflehog:latest \
            filesystem /workspace --fail \
            --exclude-paths /workspace/.pnpm-store,/workspace/node_modules,/workspace/.turbo,/workspace/.next \
            > secret-scan-report/trufflehog-report.txt 2>&1 || status=\$?

        cat secret-scan-report/trufflehog-report.txt

        if [ \${status:-0} -ne 0 ]; then
            echo ">>> Secrets detected in the codebase! Please rotate them and remove from history."
            exit 1
        fi

        echo ">>> No secrets detected."
    """
}
