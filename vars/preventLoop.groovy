def call() {
    def commitMsg = sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
    if (commitMsg.contains('[skip ci]')) {
        echo ">>> Detected [skip ci] in commit message: ${commitMsg}"
        echo '>>> Skipping this build to prevent infinite loops from GitOps updates.'
        currentBuild.result = 'ABORTED'
        error('Stopping build per [skip ci] instruction.')
    }
}
