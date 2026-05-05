def call(cfg) {
    if (!cfg.zapTarget?.trim()) {
        error('ZAP target is empty. Set the ZAP_TARGET parameter only after Gateway is reachable from the Jenkins/ZAP container.')
    }

    echo '>>> Running DAST Scan against Gateway API...'
    sh 'mkdir -p zap-report'
    withEnv(["ZAP_TARGET=${cfg.zapTarget.trim()}"]) {
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
