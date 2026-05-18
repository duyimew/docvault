def call() {
    echo '>>> Running Secret Scan (TruffleHog)...'
    sh """
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
        # --fail: Exit with code 1 if secrets are found
        # --exclude-paths: Points to a file containing paths to ignore (one per line)
        docker run --rm \
            -v ${env.WORKSPACE}:/workspace \
            trufflesecurity/trufflehog:latest \
            filesystem /workspace --fail \
            --exclude-paths /workspace/.trufflehog-exclude \
            > secret-scan-report/trufflehog-report.txt 2>&1 || status=\$?

        rm -f .trufflehog-exclude

        cat secret-scan-report/trufflehog-report.txt

        if [ ${status:-0} -ne 0 ]; then
            echo ">>> Secrets detected in the codebase! (Warning-only mode) Please rotate them and remove from history."
            unstable("Secrets detected in codebase.")
        else
            echo ">>> No secrets detected."
        fi
        """
        }
