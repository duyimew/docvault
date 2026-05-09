def call(cfg) {
    if (!cfg.zapTarget?.trim()) {
        error('ZAP target is empty. Set the ZAP_TARGET parameter only after Gateway is reachable from the Jenkins/ZAP container.')
    }

    echo '>>> Running DAST Scan against Gateway API...'
    sh 'mkdir -p zap-report'

    catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
        withEnv(["ZAP_TARGET=${cfg.zapTarget.trim()}"]) {
            sh '''
                set -eu

                status="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$ZAP_TARGET" || true)"
                case "$status" in
                    2*|3*|4*)
                        echo "ZAP target is reachable, HTTP status: $status"
                        ;;
                    *)
                        echo "ZAP target is not reachable from Jenkins/ZAP context, HTTP status: ${status:-none}"
                        exit 1
                        ;;
                esac
            '''

            sh '''
                set -eu
                docker run --rm \
                    -v "$(pwd)/zap-report:/zap/wrk:rw" \
                    ghcr.io/zaproxy/zaproxy:stable zap-baseline.py \
                    -t "$ZAP_TARGET" \
                    -r zap_report.html \
                    -J zap_report.json
            '''
        }
    }

    if (!fileExists('zap-report/zap_report.html') || !fileExists('zap-report/zap_report.json')) {
        error('ZAP did not produce zap_report.html and zap_report.json. Check the DAST stage log.')
    }

    echo '>>> ZAP reports generated under zap-report/. Review High/Critical findings before demo sign-off.'
}
