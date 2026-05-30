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
        archiveArtifacts artifacts: 'secret-scan-report/trufflehog-report.txt', allowEmptyArchive: true
        error("Secrets detected in codebase. Rotate them, remove them from Git history, or document a deliberate exception before rerunning.")
    } else {
        archiveArtifacts artifacts: 'secret-scan-report/trufflehog-report.txt', allowEmptyArchive: true
        echo ">>> No secrets detected."
    }
}
