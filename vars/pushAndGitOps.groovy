def call(cfg, builtServicesCsv) {
    def tag = resolveImageTag(cfg)
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

String resolveImageTag(cfg) {
    if (cfg.imageTag?.trim()) {
        return cfg.imageTag.trim()
    }
    if (env.IMAGE_TAG?.trim()) {
        return env.IMAGE_TAG.trim()
    }
    return "v${env.BUILD_NUMBER}"
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
    def registryArg = cfg.registryHost?.trim() ? shellQuote(cfg.registryHost.trim()) : ''
    def credentialId = cfg.registryCredentialId ?: 'dockerhub-credentials'
    def credentialType = (cfg.registryCredentialType ?: 'usernamePassword').toString().trim()

    try {
        withEnv(["DOCKER_CONFIG=${dockerConfigDir}"]) {
            if (credentialType == 'secretText') {
                def registryUsername = cfg.registryUsername?.trim()
                if (!registryUsername) {
                    error('REGISTRY_USERNAME is required when REGISTRY_CREDENTIAL_TYPE=secretText.')
                }

                withCredentials([string(credentialsId: credentialId, variable: 'DOCKER_PASS')]) {
                    sh "printf '%s' \"\$DOCKER_PASS\" | docker login -u ${shellQuote(registryUsername)} --password-stdin ${registryArg}"
                    sh "chmod 755 '${dockerConfigDir}' && chmod 644 '${dockerConfigDir}/config.json' || true"
                    pushBuiltImagesAndResolveDigests(cfg, builtList, tag, imageDigests)
                    sh "docker logout ${registryArg} || true"
                }
            } else if (credentialType == 'usernamePassword') {
                withCredentials([usernamePassword(credentialsId: credentialId, passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                    sh "printf '%s' \"\$DOCKER_PASS\" | docker login -u \"\$DOCKER_USER\" --password-stdin ${registryArg}"
                    sh "chmod 755 '${dockerConfigDir}' && chmod 644 '${dockerConfigDir}/config.json' || true"
                    pushBuiltImagesAndResolveDigests(cfg, builtList, tag, imageDigests)
                    sh "docker logout ${registryArg} || true"
                }
            } else {
                error("Unsupported REGISTRY_CREDENTIAL_TYPE='${credentialType}'. Use secretText or usernamePassword.")
            }
        }
    } finally {
        sh "rm -rf '${dockerConfigDir}'"
    }

    return imageDigests
}

def pushBuiltImagesAndResolveDigests(cfg, builtList, tag, imageDigests) {
    runPushesInBatches(cfg, builtList, tag)

    builtList.each { service ->
        def imageName = imageNameForService(cfg, service)
        def repository = resolveRepository(cfg, imageName)
        def digest = resolveImageDigest(repository, tag)
        imageDigests[service] = digest

        if (digest && shouldSignImages(cfg)) {
            signImageDigest(cfg, service, "${repository}@${digest}")
        }
    }
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
    if (cfg.pushLatest) {
        sh "docker push ${repository}:latest"
    } else {
        echo ">>> Skipping mutable latest push for ${repository}; PUSH_LATEST=false."
    }
}

def imageNameForService(cfg, service) {
    return service == cfg.webAppName ? cfg.webImageName : service
}

def valuesFileForService(cfg, service) {
    return service == cfg.webAppName ? 'web.yaml' : "${service}.yaml"
}

String resolveRepository(cfg, String service) {
    def namespace = cfg.registryNamespace?.trim() ? cfg.registryNamespace.trim() : cfg.dockerOrg
    if (cfg.registryHost?.trim()) {
        return "${cfg.registryHost.trim()}/${namespace}/${service}"
    }
    return "${namespace}/${service}"
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

boolean shouldSignImages(cfg) {
    return cfg.signImages != null && cfg.signImages.toString().equalsIgnoreCase('true')
}

void signImageDigest(cfg, String service, String imageRef) {
    def cosignImage = cfg.cosignImage ?: 'ghcr.io/sigstore/cosign/cosign:v2.4.1'
    def keyCredentialId = cfg.cosignKeyCredentialId?.trim() ?: 'cosign-private-key'
    def passwordCredentialId = cfg.cosignPasswordCredentialId?.trim() ?: 'cosign-password'
    def publicKeyCredentialId = cfg.cosignPublicKeyCredentialId?.trim()
    def tlogUpload = cfg.cosignTlogUpload != null && cfg.cosignTlogUpload.toString().equalsIgnoreCase('true')
    def signTlogFlag = tlogUpload ? '--tlog-upload=true' : '--tlog-upload=false'
    def verifyTlogFlag = tlogUpload ? '' : '--insecure-ignore-tlog=true'

    def sbomFile = "${env.WORKSPACE}/sbom-${service}.json"
    def hasSbom = fileExists(sbomFile)

    echo ">>> Signing ${service} image digest with cosign: ${imageRef}"

    withCredentials([
        string(credentialsId: keyCredentialId, variable: 'COSIGN_PRIVATE_KEY'),
        string(credentialsId: passwordCredentialId, variable: 'COSIGN_PASSWORD')
    ]) {
        withEnv([
            "COSIGN_IMAGE_REF=${imageRef}",
            "COSIGN_TLOG_FLAG=${signTlogFlag}"
        ]) {
            sh '''
                set -eu
                cosign sign --yes ${COSIGN_TLOG_FLAG} --key env://COSIGN_PRIVATE_KEY "${COSIGN_IMAGE_REF}"
            '''
        }

        if (hasSbom) {
            echo ">>> Attesting and signing SBOM for ${service}..."
            withEnv([
                "COSIGN_IMAGE_REF=${imageRef}",
                "COSIGN_TLOG_FLAG=${signTlogFlag}",
                "SBOM_FILE=${sbomFile}"
            ]) {
                sh '''
                    set -eu
                    cosign attest --yes ${COSIGN_TLOG_FLAG} --key env://COSIGN_PRIVATE_KEY --type spdxjson --predicate "${SBOM_FILE}" "${COSIGN_IMAGE_REF}"
                '''
            }
        } else {
            echo ">>> SBOM file not found at ${sbomFile}; skipping SBOM attestation."
        }
    }

    if (!publicKeyCredentialId) {
        echo '>>> COSIGN_PUBLIC_KEY_CREDENTIAL_ID is not set; skipping post-sign verification.'
        return
    }

    echo ">>> Verifying cosign signature for ${service}: ${imageRef}"
    withCredentials([string(credentialsId: publicKeyCredentialId, variable: 'COSIGN_PUBLIC_KEY')]) {
        withEnv([
            "COSIGN_IMAGE_REF=${imageRef}",
            "COSIGN_VERIFY_TLOG_FLAG=${verifyTlogFlag}"
        ]) {
            sh '''
                set -eu
                cosign verify ${COSIGN_VERIFY_TLOG_FLAG} --key env://COSIGN_PUBLIC_KEY "${COSIGN_IMAGE_REF}"
            '''
        }

        if (hasSbom) {
            echo ">>> Verifying SBOM attestation signature for ${service}..."
            withEnv([
                "COSIGN_IMAGE_REF=${imageRef}",
                "COSIGN_VERIFY_TLOG_FLAG=${verifyTlogFlag}"
            ]) {
                sh '''
                    set -eu
                    cosign verify-attestation ${COSIGN_VERIFY_TLOG_FLAG} --key env://COSIGN_PUBLIC_KEY --type spdxjson "${COSIGN_IMAGE_REF}"
                '''
            }
        }
    }
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
                    def imageName = imageNameForService(cfg, service)
                    def repository = resolveRepository(cfg, imageName)

                    sh """
                        set -eu
                        test -f '${valuesFile}'
                        sed -i -E 's#^([[:space:]]*)repository:.*#\\1repository: \"${repository}\"#' '${valuesFile}'
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

                if (cfg.createGitOpsPr) {
                    def repoPath = parseRepoPath(cfg.gitOpsRepoUrl.toString().trim())
                    def prBranch = "gitops-update-${tag}-${env.BUILD_NUMBER}"

                    echo ">>> Pull Request mode enabled. Checking out feature branch '${prBranch}'..."
                    sh "git -C '${gitOpsWorktree}' checkout -b '${prBranch}'"
                    pushWithRetry(gitOpsWorktree, prBranch)

                    def prTitle = "chore(gitops): update image references to ${tag}"
                    def prBody = "Automated PR created by Jenkins pipeline build #${env.BUILD_NUMBER} to update container image references to tag `${tag}`."
                    def payloadJson = groovy.json.JsonOutput.toJson([
                        title: prTitle,
                        head: prBranch,
                        base: targetBranch,
                        body: prBody
                    ])
                    def payloadFile = "${env.WORKSPACE}/pr-payload.json"
                    writeFile(file: payloadFile, text: payloadJson)

                    def responseFile = sh(script: 'mktemp', returnStdout: true).trim()
                    def statusFile = sh(script: 'mktemp', returnStdout: true).trim()

                    echo ">>> Creating GitHub Pull Request to target branch '${targetBranch}'..."
                    try {
                        sh """
                            set +x
                            curl -sS -X POST \\
                                -H "Accept: application/vnd.github+json" \\
                                -H "Authorization: Bearer \$GIT_PASS" \\
                                -H "X-GitHub-Api-Version: 2022-11-28" \\
                                "https://api.github.com/repos/${repoPath}/pulls" \\
                                -d @"${payloadFile}" \\
                                -o "${responseFile}" \\
                                -w "%{http_code}" > "${statusFile}"
                        """
                        def httpStatus = readFile(statusFile).trim()
                        def httpResponse = readFile(responseFile).trim()
                        echo ">>> GitHub API Response Status: ${httpStatus}"
                        echo ">>> Response Body: ${httpResponse}"

                        if (httpStatus == '201') {
                            echo ">>> Pull Request created successfully."
                        } else if (httpStatus == '422' && (httpResponse.contains('already exists') || httpResponse.contains('A pull request already exists'))) {
                            echo ">>> A Pull Request for this branch already exists. Proceeding."
                        } else {
                            error("Failed to create GitHub Pull Request. Status: ${httpStatus}, Response: ${httpResponse}")
                        }
                    } finally {
                        sh "rm -f '${payloadFile}' '${responseFile}' '${statusFile}'"
                    }
                } else {
                    pushWithRetry(gitOpsWorktree, targetBranch)
                }
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
 *   1. Save existing image repository/tag/digest from every values file on the GitOps branch.
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

    // ── Restore saved image refs so we don't accidentally downgrade registry, tags, or digests ──
    savedRefs.each { service, refs ->
        if (refs.repository || refs.tag || refs.digest) {
            def fileName = valuesFileForService(cfg, service)
            def valuesFile = "${valuesDir}/${fileName}"

            if (sh(script: "test -f '${valuesFile}'", returnStatus: true) == 0) {
                if (refs.repository) {
                    sh "sed -i -E 's#^([[:space:]]*)repository:.*#\\1repository: \"${refs.repository}\"#' '${valuesFile}'"
                }
                if (refs.tag) {
                    sh "sed -i -E 's/^([[:space:]]*)tag:.*/\\1tag: \"${refs.tag}\"/' '${valuesFile}'"
                }
                if (refs.digest) {
                    sh "sed -i -E 's/^([[:space:]]*)digest:.*/\\1digest: \"${refs.digest}\"/' '${valuesFile}'"
                }
                echo ">>> Restored image refs for ${service}: repository=${refs.repository}, tag=${refs.tag}, digest=${refs.digest}"
            }
        }
    }
}

/**
 * Read the current image repository, tag, and digest from a Helm values file.
 * Returns a map [repository: '...', tag: '...', digest: '...'] (values may be empty).
 */
def readImageRefs(valuesFile) {
    def repository = ''
    def tag = ''
    def digest = ''

    def exists = sh(script: "test -f '${valuesFile}'", returnStatus: true)
    if (exists != 0) {
        return [repository: repository, tag: tag, digest: digest]
    }

    repository = sh(
        script: "grep -E '^[[:space:]]*repository:' '${valuesFile}' | head -1 | sed -E 's/^[[:space:]]*repository:[[:space:]]*//' | tr -d '\"' || true",
        returnStdout: true
    ).trim()

    tag = sh(
        script: "grep -E '^[[:space:]]*tag:' '${valuesFile}' | head -1 | sed -E 's/^[[:space:]]*tag:[[:space:]]*//' | tr -d '\"' || true",
        returnStdout: true
    ).trim()

    digest = sh(
        script: "grep -E '^[[:space:]]*digest:' '${valuesFile}' | head -1 | sed -E 's/^[[:space:]]*digest:[[:space:]]*//' | tr -d '\"' || true",
        returnStdout: true
    ).trim()

    return [repository: repository, tag: tag, digest: digest]
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

String shellQuote(String value) {
    return "'${value.replace("'", "'\"'\"'")}'"
}

@NonCPS
String parseRepoPath(String repoUrl) {
    def matcher = repoUrl =~ /github\.com[:\/]([^\/]+)\/([^\.]+)(\.git)?/
    if (!matcher) {
        throw new IllegalArgumentException("Could not parse owner/repo from GitOps URL: ${repoUrl}")
    }
    return "${matcher[0][1]}/${matcher[0][2]}"
}
