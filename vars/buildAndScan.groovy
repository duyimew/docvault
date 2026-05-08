def call(cfg) {
    def tag = "v${env.BUILD_NUMBER}"
    def builtList = []
    def forceBuildAll = shouldForceBuildAll()
    def diffRange = resolveDiffRange()
    def changedFiles = []

    if (!forceBuildAll && diffRange) {
        changedFiles = getChangedFiles(diffRange)
        echo ">>> Change detection diff range: ${diffRange}"
        echo ">>> Changed paths detected: ${changedFiles.size()}"
    }

    if (!forceBuildAll && !diffRange) {
        echo '>>> Could not determine a reliable diff range. Building all services for safety.'
        forceBuildAll = true
    }

    if (forceBuildAll) {
        echo '>>> Force-build mode enabled; all impacted images will be rebuilt and rescanned.'
    }

    def infraChanged = forceBuildAll || changedFiles.any { it.startsWith('infra/k8s/') }
    env.INFRA_CHANGED = infraChanged ? 'true' : 'false'
    echo ">>> Infrastructure (infra/k8s/) changes detected: ${infraChanged}"

    cfg.services.each { service ->
        def changed = forceBuildAll || isServiceImpacted(service, changedFiles)

        if (changed) {
            echo ">>> Changes detected for ${service}. Building ${tag}..."
            sh "docker build -t ${cfg.dockerOrg}/${service}:${tag} -t ${cfg.dockerOrg}/${service}:latest --build-arg SERVICE_NAME=${service} -f ${cfg.backendDockerfile} ."

            echo ">>> Scanning Image ${cfg.dockerOrg}/${service}:${tag}..."
            sh """
                set -eu
                docker run --rm \\
                    -v /var/run/docker.sock:/var/run/docker.sock \\
                    ${cfg.trivyImage} \\
                    image --severity CRITICAL --exit-code 1 --no-progress ${cfg.dockerOrg}/${service}:${tag}
            """
            builtList.add(service)
        } else {
            echo ">>> No changes in services/${service}/ or libs/. Skipping build for ${service}."
        }
    }

    def webChanged = forceBuildAll || isWebImpacted(cfg, changedFiles)
    if (webChanged) {
        echo '>>> Changes detected for web app. Building...'
        sh "docker build -t ${cfg.dockerOrg}/${cfg.webImageName}:${tag} -t ${cfg.dockerOrg}/${cfg.webImageName}:latest -f ${cfg.webDockerfile} ."
        echo ">>> Scanning Image ${cfg.dockerOrg}/${cfg.webImageName}:${tag}..."
        sh """
            set -eu
            docker run --rm \\
                -v /var/run/docker.sock:/var/run/docker.sock \\
                ${cfg.trivyImage} \\
                image --severity CRITICAL --exit-code 1 --no-progress ${cfg.dockerOrg}/${cfg.webImageName}:${tag}
        """
        builtList.add(cfg.webAppName)
    }

    return builtList.join(',')
}

def shouldForceBuildAll() {
    if (env.FORCE_BUILD_ALL?.trim()) {
        return env.FORCE_BUILD_ALL.equalsIgnoreCase('true')
    }

    try {
        return params?.FORCE_BUILD_ALL?.toString()?.equalsIgnoreCase('true')
    } catch (ignored) {
        return false
    }
}

def resolveDiffRange() {
    if (env.CHANGE_TARGET?.trim()) {
        def target = env.CHANGE_TARGET.trim()
        sh(script: "git fetch --no-tags origin +refs/heads/${target}:refs/remotes/origin/${target} || true", returnStatus: true)

        def mergeBase = sh(
            script: "git merge-base HEAD origin/${target} || true",
            returnStdout: true
        ).trim()

        if (mergeBase) {
            return "${mergeBase}..HEAD"
        }
    }

    def candidates = [env.GIT_PREVIOUS_SUCCESSFUL_COMMIT, env.GIT_PREVIOUS_COMMIT]
    for (candidate in candidates) {
        if (candidate?.trim()) {
            def status = sh(script: "git cat-file -e ${candidate}^{commit}", returnStatus: true)
            if (status == 0) {
                return "${candidate}..HEAD"
            }
        }
    }

    def hasHeadParent = sh(script: 'git rev-parse --verify HEAD~1 >/dev/null 2>&1', returnStatus: true)
    if (hasHeadParent == 0) {
        return 'HEAD~1..HEAD'
    }

    return null
}

def getChangedFiles(String diffRange) {
    def output = sh(script: "git diff --name-only ${diffRange}", returnStdout: true).trim()
    if (!output) {
        return []
    }

    return output
        .split('\n')
        .collect { it.trim() }
        .findAll { it }
}

boolean isServiceImpacted(String service, List changedFiles) {
    return changedFiles.any { path ->
        path.startsWith("services/${service}/") || isGlobalBackendImpact(path)
    }
}

boolean isWebImpacted(cfg, List changedFiles) {
    return changedFiles.any { path ->
        path.startsWith('apps/web/') || isGlobalWebImpact(path, cfg)
    }
}

boolean isGlobalBackendImpact(String path) {
    return path.startsWith('libs/') ||
        path == 'Dockerfile.backend' ||
        path == 'package.json' ||
        path == 'pnpm-lock.yaml' ||
        path == 'pnpm-workspace.yaml' ||
        path == 'turbo.json'
}

boolean isGlobalWebImpact(String path, cfg) {
    return path == cfg.webDockerfile ||
        path == 'package.json' ||
        path == 'pnpm-lock.yaml' ||
        path == 'pnpm-workspace.yaml' ||
        path == 'turbo.json'
}
