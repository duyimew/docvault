def call(cfg) {
    def namespace = cfg.argocdNamespace ?: 'argocd'
    def apps = cfg.argocdApps ?: []
    def timeoutSeconds = (cfg.argocdTimeoutSeconds ?: '300').toString()

    if (!apps) {
        error('No Argo CD applications configured for health check.')
    }

    echo ">>> Waiting for Argo CD applications in namespace '${namespace}' to become Synced/Healthy: ${apps.join(', ')}"

    withEnv([
        "ARGOCD_NAMESPACE=${namespace}",
        "ARGOCD_APPS=${apps.join(' ')}",
        "ARGOCD_TIMEOUT_SECONDS=${timeoutSeconds}"
    ]) {
        sh '''
            set -eu

            command -v kubectl >/dev/null 2>&1 || {
                echo "kubectl is not available on the Jenkins agent. Disable RUN_ARGO_HEALTH_CHECK or install/configure kubectl."
                exit 1
            }

            deadline=$(( $(date +%s) + ${ARGOCD_TIMEOUT_SECONDS:-300} ))

            while :; do
                not_ready=""
                echo "Current Argo CD application status:"

                for app in $ARGOCD_APPS; do
                    status="$(kubectl -n "$ARGOCD_NAMESPACE" get application "$app" -o jsonpath='{.status.sync.status}{" "}{.status.health.status}' 2>/dev/null || true)"

                    if [ -z "$status" ]; then
                        sync_status="Missing"
                        health_status="Missing"
                    else
                        sync_status="$(printf '%s' "$status" | awk '{print $1}')"
                        health_status="$(printf '%s' "$status" | awk '{print $2}')"
                    fi

                    printf '  %s: sync=%s health=%s\\n' "$app" "$sync_status" "$health_status"

                    if [ "$sync_status" != "Synced" ] || [ "$health_status" != "Healthy" ]; then
                        not_ready="$not_ready $app"
                    fi
                done

                if [ -z "$not_ready" ]; then
                    echo "All configured Argo CD applications are Synced/Healthy."
                    exit 0
                fi

                if [ "$(date +%s)" -ge "$deadline" ]; then
                    echo "Timed out waiting for Argo CD applications:$not_ready"
                    kubectl -n "$ARGOCD_NAMESPACE" get applications || true
                    exit 1
                fi

                sleep 15
            done
        '''
    }
}
