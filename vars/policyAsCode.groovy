def call(cfg) {
    echo '>>> Running Policy as Code Scan (Kyverno CLI)...'

    def status = sh(
        script: """
        set -eu
        mkdir -p policy-report
        mkdir -p policy-rendered

        echo ">>> Rendering DocVault Helm values for policy checks..."
        for f in infra/k8s/values/*.yaml; do
            name="\$(basename "\$f" .yaml)"
            docker run --rm \\
                -v ${env.WORKSPACE}:/workspace \\
                -w /workspace \\
                ${cfg.helmImage ?: 'alpine/helm:3.16.4'} \\
                template "docvault-\$name" infra/k8s/charts/docvault-service \\
                -n docvault \\
                -f "\$f" \\
                > "policy-rendered/\$name.yaml"
        done

        echo ">>> Scanning infra/k8s manifests and rendered Helm output against Kyverno policies..."

        RESOURCES=""
        for f in infra/k8s/infra-deps/*.yaml; do
            if grep -q "kind:" "\$f"; then
                RESOURCES="\$RESOURCES --resource /workspace/\$f"
            fi
        done

        for f in policy-rendered/*.yaml; do
            if grep -q "kind:" "\$f"; then
                RESOURCES="\$RESOURCES --resource /workspace/\$f"
            fi
        done

        if [ -z "\$RESOURCES" ]; then
            echo "No valid Kubernetes manifests found. Skipping scan."
            exit 0
        fi

        set +e
        docker run --rm \\
            -v ${env.WORKSPACE}:/workspace \
            -w /workspace \
            ${cfg.kyvernoImage ?: 'ghcr.io/kyverno/kyverno-cli:v1.12.0'} \
            apply /workspace/policies/kyverno \
            \$RESOURCES \
            --detailed-results \
            > policy-report/kyverno-report.txt 2>&1
        status=\$?
        set -e

        cat policy-report/kyverno-report.txt
        exit "\$status"
        """,
        returnStatus: true
    )

    archiveArtifacts artifacts: 'policy-report/kyverno-report.txt,policy-rendered/*.yaml', allowEmptyArchive: true

    if (status != 0) {
        error("Policy as Code violations detected.")
    } else {
        echo ">>> Policy as Code scan passed."
    }
}
