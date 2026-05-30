def call(cfg) {
    def tag = "v${env.BUILD_NUMBER}"
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

    def buildTargets = []

    cfg.services.each { service ->
        def changed = forceBuildAll || isServiceImpacted(service, changedFiles)

        if (changed) {
            buildTargets << [
                name: service,
                repository: resolveRepository(cfg, service),
                dockerfile: cfg.backendDockerfile,
                buildArgs: [SERVICE_NAME: service],
            ]
        } else {
            echo ">>> No changes in services/${service}/ or libs/. Skipping build for ${service}."
        }
    }

    def webChanged = forceBuildAll || isWebImpacted(cfg, changedFiles)
    if (webChanged) {
        buildTargets << [
            name: cfg.webAppName,
            repository: resolveRepository(cfg, cfg.webImageName),
            dockerfile: cfg.webDockerfile,
            buildArgs: [
                NEXT_PUBLIC_APP_NAME: 'DocVault',
                NEXT_PUBLIC_API_BASE_URL: '/api',
                GATEWAY_URL: 'http://docvault-gateway:3000',
            ],
        ]
    }

    def trivyDbReady = buildTargets ? warmTrivyCache(cfg) : false
    def builtList = buildTargets ? runBuildsInBatches(cfg, buildTargets, tag, trivyDbReady) : []

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

boolean warmTrivyCache(cfg) {
    echo '>>> Warming Trivy cache...'
    def status = sh(
        script: """
            set +e
            docker volume create trivy-cache >/dev/null
            docker run --rm \\
                -v trivy-cache:/root/.cache/trivy \\
                ${cfg.trivyImage} \\
                image --download-db-only --no-progress
        """,
        returnStatus: true
    )

    if (status != 0) {
        echo '>>> WARNING: Could not warm Trivy cache. Scans will use per-target caches.'
        return false
    }

    return true
}

List runBuildsInBatches(cfg, List buildTargets, String tag, boolean trivyDbReady) {
    def builtList = []
    def batchSize = (cfg.buildParallelism ?: 3) as Integer
    if (batchSize < 1) {
        batchSize = 1
    }

    def batches = buildTargets.collate(batchSize)
    batches.eachWithIndex { batch, index ->
        echo ">>> Build batch ${index + 1}/${batches.size()} with ${batch.size()} target(s)"

        def branches = [:]
        batch.each { target ->
            def currentTarget = target
            branches[currentTarget.name] = {
                buildTarget(cfg, currentTarget, tag)
            }
        }

        parallel branches

        batch.each { target ->
            scanTarget(cfg, target, tag, trivyDbReady)
        }

        builtList.addAll(batch.collect { it.name })
    }

    return builtList
}

void buildTarget(cfg, Map target, String tag) {
    def repository = target.repository
    def dockerfile = target.dockerfile
    def buildArgs = target.buildArgs ?: [:]
    def cacheFrom = "${repository}:latest"

    echo ">>> Changes detected for ${target.name}. Building ${tag}..."

    sh """
        set -eu
        export DOCKER_BUILDKIT=1
        docker pull '${cacheFrom}' >/dev/null 2>&1 || true
        docker build \\
            --build-arg BUILDKIT_INLINE_CACHE=1 \\
            --cache-from '${cacheFrom}' \\
            ${buildArgsToFlags(buildArgs)} \\
            -t '${repository}:${tag}' \\
            -t '${repository}:latest' \\
            -f '${dockerfile}' \\
            .
    """
}

void scanTarget(cfg, Map target, String tag, boolean trivyDbReady) {
    def repository = target.repository
    def trivyCacheVolume = trivyDbReady ? 'trivy-cache' : "trivy-cache-${target.name}"
    def trivyDbFlags = trivyDbReady ? '--skip-db-update' : ''

    echo ">>> Scanning Image ${repository}:${tag}..."
    sh """
        set -eu
        docker run --rm \\
            -v /var/run/docker.sock:/var/run/docker.sock \\
            -v ${trivyCacheVolume}:/root/.cache/trivy \\
            ${cfg.trivyImage} \\
            image ${trivyDbFlags} --scanners vuln --severity CRITICAL --exit-code 1 --no-progress '${repository}:${tag}'
    """
}

String buildArgsToFlags(Map buildArgs) {
    if (!buildArgs) {
        return ''
    }

    return buildArgs.collect { key, value ->
        "--build-arg ${key}=${shellQuote(value.toString())}"
    }.join(' ')
}

String shellQuote(String value) {
    return "'${value.replace("'", "'\"'\"'")}'"
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

String resolveRepository(cfg, String service) {
    def namespace = cfg.registryNamespace?.trim() ? cfg.registryNamespace.trim() : cfg.dockerOrg
    if (cfg.registryHost?.trim()) {
        return "${cfg.registryHost.trim()}/${namespace}/${service}"
    }
    return "${namespace}/${service}"
}
