def call(cfg) {
    echo '>>> Running Policy as Code Scan (Kyverno CLI)...'
    sh """
        set -eu
        mkdir -p policy-report

        echo ">>> Scanning infra/k8s/infra-deps against Kyverno policies..."

        # Use kyverno apply to check policies against resources
        # We allow it to fail the shell if violations are found (default behavior of kyverno apply if there are failures)
        docker run --rm \
            -v ${env.WORKSPACE}:/workspace \
            -w /workspace \
            ghcr.io/kyverno/kyverno-cli:v1.12.0 \
            apply /workspace/policies/kyverno \
            --resource /workspace/infra/k8s/infra-deps \
            --detailed-results \
            > policy-report/kyverno-report.txt 2>&1 || status=\$?

        cat policy-report/kyverno-report.txt

        if grep -q "fail" policy-report/kyverno-report.txt; then
            echo ">>> Policy violations detected!"
            exit 1
        fi

        echo ">>> Policy as Code scan passed."
    """
}
