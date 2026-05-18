def call() {
    echo '>>> Running Secret Scan (TruffleHog)...'
    
    // 1. Run the scan and capture exit code
    def status = sh(
        script: """
            set -eu
            mkdir -p secret-scan-report

            echo ">>> Creating exclusion file for TruffleHog..."
            cat > .trufflehog-exclude <<EOF
.pnpm-store
node_modules
.turbo
.next
infra/k8s/infra-deps/harbor-values.yaml
EOF

            echo ">>> Scanning repository for leaked secrets..."

            # Run TruffleHog filesystem scan
            # We use set +e to capture the exit code manually
            set +e
            docker run --rm \
                -v ${env.WORKSPACE}:/workspace \
                trufflesecurity/trufflehog:latest \
                filesystem /workspace --fail \
                --exclude-paths /workspace/.trufflehog-exclude \
                > secret-scan-report/trufflehog-report.txt 2>&1
            exit_code=\$?
            set -e

            rm -f .trufflehog-exclude
            cat secret-scan-report/trufflehog-report.txt
            exit \$exit_code
        """,
        returnStatus: true
    )

    // 2. Evaluate the status in Groovy
    if (status != 0) {
        echo ">>> Secrets detected in the codebase! (Warning-only mode) Please rotate them and remove from history."
        unstable("Secrets detected in codebase.")
    } else {
        echo ">>> No secrets detected."
    }
}
