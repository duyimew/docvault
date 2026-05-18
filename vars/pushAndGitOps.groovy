def call(cfg, builtServicesCsv) {
    def tag = "v${env.BUILD_NUMBER}"
    def builtList = parseBuiltServices(builtServicesCsv)
    def infraChanged = env.INFRA_CHANGED == 'true'

    if (!builtList && !infraChanged) {
        echo '>>> No built services and no infra changes. Nothing to publish.'
        return
    }

    def targetBranch = cfg.gitOpsBranch
    echo ">>> GitOps target branch: ${targetBranch}"

    def imageDigests = [:]
    if (builtList) {
        imageDigests = pushImages(cfg, builtList, tag)
    }

    updateGitOpsBranch(cfg, builtList, tag, imageDigests, targetBranch, infraChanged)
}

def parseBuiltServices(builtServicesCsv) {
    if (!builtServicesCsv?.trim()) {
        return []
    }

    return builtServicesCsv
        .split(',')
        .collect { it.trim() }
        .findAll { it }
        .unique()
}

def pushImages(cfg, builtList, tag) {
    echo ">>> Logging into registry ${cfg.registryHost ?: 'Docker Hub'}..."

    def dockerConfigDir = sh(script: 'mktemp -d', returnStdout: true).trim()
    def imageDigests = [:]

    try {
        withEnv(["DOCKER_CONFIG=${dockerConfigDir}"]) {
            withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin ${cfg.registryHost ?: ""}'

                runPushesInBatches(cfg, builtList, tag)

                builtList.each { service ->
                    def imageName = imageNameForService(cfg, service)
                    def repository = resolveRepository(cfg, imageName)
                    imageDigests[service] = resolveImageDigest(repository, tag)
                }

                sh 'docker logout ${cfg.registryHost ?: ""} || true'
            }
        }
    } finally {
        sh "rm -rf '${dockerConfigDir}'"
    }

    return imageDigests
}

def runPushesInBatches(cfg, builtList, tag) {
    def batchSize = (cfg.pushParallelism ?: 3) as Integer
    if (batchSize < 1) {
        batchSize = 1
    }

    def batches = builtList.collate(batchSize)
    batches.eachWithIndex { batch, index ->
        echo ">>> Push batch ${index + 1}/${batches.size()} with ${batch.size()} image(s)"

        def branches = [:]
        batch.each { service ->
            def currentService = service
            branches[currentService] = {
                pushImage(cfg, currentService, tag)
            }
        }

        parallel branches
    }
}

def pushImage(cfg, service, tag) {
    def imageName = imageNameForService(cfg, service)
    def repository = resolveRepository(cfg, imageName)
    def taggedImage = "${repository}:${tag}"

    echo ">>> Pushing ${taggedImage} to registry..."
    sh "docker push ${taggedImage}"
    sh "docker push ${repository}:latest"
}

def imageNameForService(cfg, service) {
    return service == cfg.webAppName ? cfg.webImageName : service
}

def valuesFileForService(cfg, service) {
    return service == cfg.webAppName ? 'web.yaml' : "${service}.yaml"
}

String resolveRepository(cfg, String service) {
    if (cfg.registryHost?.trim()) {
        return "${cfg.registryHost.trim()}/${cfg.dockerOrg}/${service}"
    }
    return "${cfg.dockerOrg}/${service}"
}

def resolveImageDigest(repository, tag) {
    def imageRef = "${repository}:${tag}"
    def digest = sh(
        script: """
            set +e
            digest=""
            if docker buildx version >/dev/null 2>&1; then
                digest=\$(docker buildx imagetools inspect '${imageRef}' 2>/dev/null | awk '/^Digest:/ {print \$2; exit}')
            fi
            if [ -z "\$digest" ] || [ "\$digest" = "null" ]; then
                digest=\$(docker inspect --format='{{index .RepoDigests 0}}' '${imageRef}' 2>/dev/null | sed 's/.*@//')
            fi
            [ "\$digest" = "null" ] && digest=""
            printf '%s' "\$digest"
        """,
        returnStdout: true
    ).trim()

    if (!digest) {
        echo ">>> WARNING: Could not resolve digest for ${imageRef}. Helm values will use tag only."
        return ''
    }

    echo ">>> Resolved ${imageRef} digest: ${digest}"
    return digest
}

def updateGitOpsBranch(cfg, builtList, tag, imageDigests, targetBranch, infraChanged) {
    echo '>>> Updating GitOps branch with new references...'

    def askPassScript = '.git-askpass.sh'
    def gitOpsWorktree = sh(script: 'mktemp -d', returnStdout: true).trim()

    withCredentials([usernamePassword(credentialsId: 'github-credentials', passwordVariable: 'GIT_PASS', usernameVariable: 'GIT_USER')]) {
        try {
            sh """
                set -eu
                cat > '${askPassScript}' <<'EOF'
#!/bin/sh
case "\$1" in
    *Username*) printf '%s\\n' "\$GIT_USER" ;;
    *Password*) printf '%s\\n' "\$GIT_PASS" ;;
    *) printf '\\n' ;;
esac
EOF
                chmod 700 '${askPassScript}'
            """

            withEnv(["GIT_ASKPASS=${env.WORKSPACE}/${askPassScript}", 'GIT_TERMINAL_PROMPT=0']) {
                def branchExists = sh(
                    script: "git ls-remote --exit-code --heads ${cfg.gitOpsRepoUrl} ${targetBranch}",
                    returnStatus: true
                )
                if (branchExists != 0) {
                    error("GitOps branch '${targetBranch}' was not found on ${cfg.gitOpsRepoUrl}. Create the branch before running this pipeline.")
                }

                sh "git clone --single-branch --branch '${targetBranch}' '${cfg.gitOpsRepoUrl}' '${gitOpsWorktree}'"

                // ── Step 1: Sync infra/k8s files if infrastructure changed ──
                if (infraChanged) {
                    syncInfraFiles(cfg, gitOpsWorktree)
                }

                // ── Step 2: Update image tags for newly built services ──
                builtList.each { service ->
                    def fileName = valuesFileForService(cfg, service)
                    def valuesFile = "${gitOpsWorktree}/${cfg.helmValuesDir}/${fileName}"
                    def digest = imageDigests[service] ?: ''

                    sh """
                        set -eu
                        test -f '${valuesFile}'
                        sed -i -E 's/^([[:space:]]*)tag:.*/\\1tag: \"${tag}\"/' '${valuesFile}'
                        sed -i -E 's/^([[:space:]]*)digest:.*/\\1digest: \"${digest}\"/' '${valuesFile}'
                    """
                }

                def changed = sh(
                    script: "git -C '${gitOpsWorktree}' status --porcelain",
                    returnStdout: true
                ).trim()
                if (!changed) {
                    echo '>>> No updates detected. Skipping GitOps commit/push.'
                    return
                }

                def commitParts = []
                if (builtList) {
                    commitParts.add("image refs for ${builtList.join(',')} to ${tag}")
                }
                if (infraChanged) {
                    commitParts.add("infra/k8s manifests")
                }
                def commitMsg = "chore(gitops): update ${commitParts.join(' + ')} [skip ci]"

                sh """
                    set -eu
                    git -C '${gitOpsWorktree}' config user.email "daithang59@users.noreply.github.com"
                    git -C '${gitOpsWorktree}' config user.name "daithang59"
                    git -C '${gitOpsWorktree}' add -A
                    git -C '${gitOpsWorktree}' commit -m "${commitMsg}"
                """

                pushWithRetry(gitOpsWorktree, targetBranch)
            }
        } finally {
            sh "rm -f '${askPassScript}'"
            sh "rm -rf '${gitOpsWorktree}'"
        }
    }
}

/**
 * Sync infra/k8s files from the source workspace to the GitOps worktree.
 *
 * Strategy:
 *   1. Save existing image tag/digest from every values file on the GitOps branch.
 *   2. Copy the full infra/k8s directory from the workspace (charts, infra-deps, values).
 *   3. Restore the saved tag/digest so existing deployments keep their current image refs.
 *
 * When images are ALSO built in the same run, the caller will overwrite the
 * tag/digest for those specific services afterwards — which is correct.
 */
def syncInfraFiles(cfg, gitOpsWorktree) {
    echo '>>> Syncing infra/k8s files to GitOps branch...'

    def valuesDir = "${gitOpsWorktree}/${cfg.helmValuesDir}"

    // ── Save current image refs from GitOps branch before overwriting ──
    def allServiceKeys = cfg.services.collect { it } + [cfg.webAppName]
    def savedRefs = [:]
    allServiceKeys.each { service ->
        def fileName = valuesFileForService(cfg, service)
        def valuesFile = "${valuesDir}/${fileName}"
        savedRefs[service] = readImageRefs(valuesFile)
    }

    echo ">>> Saved image refs from GitOps branch: ${savedRefs}"

    // ── Copy infra/k8s from workspace to GitOps worktree ──
    def srcDir = "${env.WORKSPACE}/infra/k8s"
    def destDir = "${gitOpsWorktree}/infra/k8s"

    sh """
        set -eu
        rm -rf '${destDir}'
        cp -a '${srcDir}' '${destDir}'
    """

    echo '>>> infra/k8s files synced.'

    // ── Restore saved image refs so we don't accidentally downgrade tags ──
    savedRefs.each { service, refs ->
        if (refs.tag || refs.digest) {
            def fileName = valuesFileForService(cfg, service)
            def valuesFile = "${valuesDir}/${fileName}"

            if (sh(script: "test -f '${valuesFile}'", returnStatus: true) == 0) {
                if (refs.tag) {
                    sh "sed -i -E 's/^([[:space:]]*)tag:.*/\\1tag: \"${refs.tag}\"/' '${valuesFile}'"
                }
                if (refs.digest) {
                    sh "sed -i -E 's/^([[:space:]]*)digest:.*/\\1digest: \"${refs.digest}\"/' '${valuesFile}'"
                }
                echo ">>> Restored image refs for ${service}: tag=${refs.tag}, digest=${refs.digest}"
            }
        }
    }
}

/**
 * Read the current image tag and digest from a Helm values file.
 * Returns a map [tag: '...', digest: '...'] (either may be empty).
 */
def readImageRefs(valuesFile) {
    def tag = ''
    def digest = ''

    def exists = sh(script: "test -f '${valuesFile}'", returnStatus: true)
    if (exists != 0) {
        return [tag: tag, digest: digest]
    }

    tag = sh(
        script: "grep -E '^[[:space:]]*tag:' '${valuesFile}' | head -1 | sed -E 's/^[[:space:]]*tag:[[:space:]]*//' | tr -d '\"' || true",
        returnStdout: true
    ).trim()

    digest = sh(
        script: "grep -E '^[[:space:]]*digest:' '${valuesFile}' | head -1 | sed -E 's/^[[:space:]]*digest:[[:space:]]*//' | tr -d '\"' || true",
        returnStdout: true
    ).trim()

    return [tag: tag, digest: digest]
}

def pushWithRetry(gitOpsWorktree, targetBranch) {
    def pushed = false

    for (int attempt = 1; attempt <= 3; attempt++) {
        def pushStatus = sh(
            script: "git -C '${gitOpsWorktree}' push origin HEAD:${targetBranch}",
            returnStatus: true
        )
        if (pushStatus == 0) {
            pushed = true
            echo ">>> GitOps push successful on attempt ${attempt}."
            break
        }

        if (attempt < 3) {
            echo ">>> GitOps push failed (attempt ${attempt}/3). Rebasing and retrying."
            sh """
                set -eu
                git -C '${gitOpsWorktree}' fetch origin '${targetBranch}'
                git -C '${gitOpsWorktree}' rebase 'origin/${targetBranch}'
            """
        }
    }

    if (!pushed) {
        error("Failed to push GitOps update to branch '${targetBranch}' after 3 attempts.")
    }
}
