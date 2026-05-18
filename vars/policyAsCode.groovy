def call(cfg) {
    echo '>>> Running Policy as Code Scan (Kyverno CLI)...'
    
    // 1. Run the scan and save result to report
    sh """
        set -eu
        mkdir -p policy-report

        echo ">>> Scanning infra/k8s/infra-deps against Kyverno policies..."

        # Filter for actual Kubernetes manifests (files containing 'kind:') to avoid parsing errors on values files
        RESOURCES=""
        for f in infra/k8s/infra-deps/*.yaml; do
            if grep -q "kind:" "\$f"; then
                RESOURCES="\$RESOURCES --resource /workspace/\$f"
            fi
        done

        if [ -z "\$RESOURCES" ]; then
            echo "No valid Kubernetes manifests found in infra/k8s/infra-deps. Skipping scan."
            exit 0
        fi

        # Use kyverno apply to check policies against resources
        # Use set +e or || true so the shell doesn't exit on Kyverno failure
        set +e
        docker run --rm \
            -v ${env.WORKSPACE}:/workspace \
            -w /workspace \
            ghcr.io/kyverno/kyverno-cli:v1.12.0 \
            apply /workspace/policies/kyverno \
            \$RESOURCES \
            --detailed-results \
            > policy-report/kyverno-report.txt 2>&1
        set -e

        cat policy-report/kyverno-report.txt
    """

    // 2. Check the report file for failures using Groovy
    def report = readFile('policy-report/kyverno-report.txt')
    if (report.contains('fail')) {
        echo ">>> Policy violations detected! (Warning-only mode)"
        unstable("Policy as Code violations detected.")
    } else {
        echo ">>> Policy as Code scan passed."
    }
}
