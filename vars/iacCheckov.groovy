def call(Map cfg = [:]) {
    echo '>>> Running Checkov IaC Scan (Terraform/K8s/Helm only)...'

    String checkovImage = cfg.checkovImage ?: 'bridgecrew/checkov:latest'
    String skipChecks   = cfg.skipChecks ?: ''
    String skipPaths    = cfg.skipPaths ?: 'infra/k8s/infra-deps'
    String extraArgs    = cfg.extraArgs ?: ''

    withEnv([
        "CHECKOV_IMAGE=${checkovImage}",
        "CHECKOV_SKIP_CHECKS=${skipChecks}",
        "CHECKOV_SKIP_PATHS=${skipPaths}",
        "CHECKOV_EXTRA_ARGS=${extraArgs}"
    ]) {
        sh '''
            set -eu
            mkdir -p checkov-report

            TARGET_ARGS=""

            [ -d "$WORKSPACE/infra/k8s" ] && TARGET_ARGS="$TARGET_ARGS --directory /repo/infra/k8s"
            [ -d "$WORKSPACE/infra/argocd" ] && TARGET_ARGS="$TARGET_ARGS --directory /repo/infra/argocd"
            [ -d "$WORKSPACE/infra/argocd-apps" ] && TARGET_ARGS="$TARGET_ARGS --directory /repo/infra/argocd-apps"
            [ -d "$WORKSPACE/infra/terraform" ] && TARGET_ARGS="$TARGET_ARGS --directory /repo/infra/terraform"
            [ -d "$WORKSPACE/charts" ] && TARGET_ARGS="$TARGET_ARGS --directory /repo/charts"

            if [ -z "$TARGET_ARGS" ]; then
              echo "No IaC directories found. Skipping Checkov."
              exit 0
            fi

            SKIP_ARGS=""
            if [ -n "${CHECKOV_SKIP_CHECKS:-}" ]; then
              SKIP_ARGS="$SKIP_ARGS --skip-check ${CHECKOV_SKIP_CHECKS}"
            fi

            if [ -n "${CHECKOV_SKIP_PATHS:-}" ]; then
              OLDIFS=$IFS
              IFS=','
              for p in $CHECKOV_SKIP_PATHS; do
                SKIP_ARGS="$SKIP_ARGS --skip-path $p"
              done
              IFS=$OLDIFS
            fi

            EXTRA_ARGS=""
            if [ -n "${CHECKOV_EXTRA_ARGS:-}" ]; then
              EXTRA_ARGS="${CHECKOV_EXTRA_ARGS}"
            fi

            status=0
            if docker run --rm \
                -v "$WORKSPACE:/repo" \
                -w /repo \
                "$CHECKOV_IMAGE" \
                $TARGET_ARGS \
                --framework terraform,kubernetes,helm \
                $SKIP_ARGS \
                $EXTRA_ARGS \
                --output cli \
                > checkov-report/checkov-report.txt 2>&1
            then
                status=0
            else
                status=$?
            fi

            cat checkov-report/checkov-report.txt
            [ "$status" -eq 0 ] || exit "$status"
        '''
    }
}
