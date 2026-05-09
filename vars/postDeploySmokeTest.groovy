def call(cfg) {
    if (!cfg.deployTargetUrl?.trim()) {
        echo '>>> DEPLOY_TARGET_URL is not set. Skipping post-deploy smoke test.'
        return
    }

    def target = cfg.deployTargetUrl.trim().replaceAll('/+$', '')

    echo ">>> Running post-deploy smoke test against ${target}"

    withEnv(["DEPLOY_TARGET_URL=${target}"]) {
        sh '''
            set -eu

            check_url() {
                name="$1"
                url="$2"
                expected="$3"

                attempt=1
                while [ "$attempt" -le 30 ]; do
                    status="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$url" || true)"
                    echo "$name attempt $attempt/30: $url -> HTTP ${status:-none}"

                    if [ "$expected" = "root" ]; then
                        case "$status" in
                            2*|3*) return 0 ;;
                        esac
                    elif [ "$expected" = "health" ]; then
                        case "$status" in
                            2*) return 0 ;;
                        esac
                    else
                        echo "Unknown smoke check expectation: $expected"
                        return 1
                    fi

                    if [ "$attempt" -eq 30 ]; then
                        echo "$name smoke check failed for $url. Last HTTP status: ${status:-none}"
                        return 1
                    fi

                    attempt=$((attempt + 1))
                    sleep 10
                done
            }

            check_url "web-root" "$DEPLOY_TARGET_URL" "root"
            check_url "api-health" "$DEPLOY_TARGET_URL/api/health" "health"

            echo "Post-deploy smoke test passed."
        '''
    }
}
