@Library('docvault@feat/kyverno-k8s-manifests') _

def cfg = [:]
def changeSet = [:]
def builtServicesCsv = ''

pipeline {
    agent { label 'docker-agent-alpine-ubuntu-vm' }

    options {
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(
            name: 'FORCE_BUILD_ALL',
            defaultValue: false,
            description: 'Rebuild and rescan all images regardless of detected file changes.'
        )
        string(
            name: 'RELEASE_BRANCH',
            defaultValue: 'main',
            description: 'Trusted branch that is allowed to publish images and update GitOps.'
        )
        string(
            name: 'REGISTRY_HOST',
            defaultValue: 'harbor.docvault.id.vn',
            description: 'Container registry host without protocol. Use harbor.docvault.id.vn for the DocVault Harbor registry.'
        )
        string(
            name: 'REGISTRY_NAMESPACE',
            defaultValue: 'docvault-dev',
            description: 'Registry namespace/project. For Harbor dev pushes use docvault-dev; for Docker Hub use the Docker Hub username/org.'
        )
        string(
            name: 'REGISTRY_CREDENTIAL_ID',
            defaultValue: 'harbor-docvault-dev-robot-token',
            description: 'Jenkins credential ID for docker login. Harbor AWS Secrets Manager credential uses Secret Text.'
        )
        choice(
            name: 'REGISTRY_CREDENTIAL_TYPE',
            choices: ['secretText', 'usernamePassword'],
            description: 'Credential binding type. Use secretText for Harbor robot token from AWS Secrets Manager; usernamePassword for Docker Hub.'
        )
        string(
            name: 'REGISTRY_USERNAME',
            defaultValue: 'robot$docvault-dev+jenkins-push',
            description: 'Registry username used when REGISTRY_CREDENTIAL_TYPE=secretText. For Harbor robot accounts this includes robot$.'
        )
        booleanParam(
            name: 'PUSH_LATEST',
            defaultValue: false,
            description: 'Also push the mutable latest tag. Keep false when Harbor tag immutability is enabled.'
        )
        booleanParam(
            name: 'REGISTRY_BUILD_CACHE',
            defaultValue: true,
            description: 'Use BuildKit registry cache in Harbor, for example <image>:buildcache, to speed up repeated Docker builds.'
        )
        string(
            name: 'REGISTRY_BUILD_CACHE_SUFFIX',
            defaultValue: 'buildcache',
            description: 'Mutable tag suffix used only for BuildKit registry cache. This is not deployed by Argo CD.'
        )
        string(
            name: 'ALPINE_SECURITY_REFRESH',
            defaultValue: 'manual',
            description: 'Change this value intentionally, for example weekly, to refresh Alpine security packages without invalidating Docker cache every build.'
        )
        booleanParam(
            name: 'SIGN_IMAGES',
            defaultValue: true,
            description: 'Sign pushed image digests with cosign. Requires cosign-private-key and cosign-password credentials.'
        )
        string(
            name: 'COSIGN_KEY_CREDENTIAL_ID',
            defaultValue: 'cosign-private-key',
            description: 'Jenkins Secret text credential containing the encrypted cosign private key.'
        )
        string(
            name: 'COSIGN_PASSWORD_CREDENTIAL_ID',
            defaultValue: 'cosign-password',
            description: 'Jenkins Secret text credential containing the cosign private key password.'
        )
        string(
            name: 'COSIGN_PUBLIC_KEY_CREDENTIAL_ID',
            defaultValue: '',
            description: 'Optional Jenkins Secret text credential containing the cosign public key for post-sign verification.'
        )
        booleanParam(
            name: 'COSIGN_TLOG_UPLOAD',
            defaultValue: false,
            description: 'Upload signatures to the Sigstore transparency log. Keep false for a private lab registry unless you intentionally want public transparency log entries.'
        )
        booleanParam(
            name: 'ENFORCE_SONAR_QG',
            defaultValue: true,
            description: 'Fail the pipeline when the SonarQube Quality Gate fails or times out.'
        )
        string(
            name: 'GITOPS_BRANCH',
            defaultValue: 'gitops-testing',
            description: 'GitOps branch used for Helm values tag updates (create this branch before enabling updates).'
        )
        string(
            name: 'DEPLOY_TARGET_URL',
            defaultValue: '',
            description: 'Reachable deployed web base URL for post-deploy smoke tests, for example http://<node-ip>:30006.'
        )
        booleanParam(
            name: 'RUN_ARGO_HEALTH_CHECK',
            defaultValue: false,
            description: 'Check configured Argo CD Applications are Synced/Healthy after GitOps push. Requires kubectl access from Jenkins.'
        )
        string(
            name: 'ARGOCD_NAMESPACE',
            defaultValue: 'argocd',
            description: 'Namespace where Argo CD Application resources are installed.'
        )
        string(
            name: 'ARGOCD_APPS',
            defaultValue: 'docvault-gateway docvault-metadata docvault-document-service docvault-workflow-service docvault-audit-service docvault-notification-service docvault-web',
            description: 'Space or comma separated Argo CD Application names to wait for Synced/Healthy.'
        )
        string(
            name: 'ARGOCD_TIMEOUT_SECONDS',
            defaultValue: '300',
            description: 'Maximum seconds to wait for all configured Argo CD Applications to become Synced/Healthy.'
        )
        string(
            name: 'KUBECONFIG_CREDENTIAL_ID',
            defaultValue: '',
            description: 'Optional Jenkins Secret file credential ID containing kubeconfig for Argo CD health checks, for example jenkins-argocd-kubeconfig.'
        )
        booleanParam(
            name: 'RUN_ZAP',
            defaultValue: false,
            description: 'Run DAST scan after deploy target is reachable.'
        )
        string(
            name: 'ZAP_TARGET',
            defaultValue: '',
            description: 'Reachable web base URL for ZAP baseline scan, for example http://<node-ip>:30006. Required only when RUN_ZAP=true.'
        )
        booleanParam(
            name: 'USE_NVD_KEY',
            defaultValue: true,
            description: 'Use NVD API key for Dependency Check to bypass rate limits (requires "nvd-api-key" credential).'
        )
        booleanParam(
            name: 'DEPENDENCY_CHECK_NO_UPDATE',
            defaultValue: true,
            description: 'Use the cached Dependency Check vulnerability database. If the cache is empty, the helper will allow one update to initialize it.'
        )
        booleanParam(
            name: 'ALLOW_DEPENDENCY_CHECK_FAILURE',
            defaultValue: true,
            description: 'Temporarily continue the pipeline when Dependency Check fails. The stage/build will be marked unstable.'
        )
        booleanParam(
            name: 'CREATE_GITOPS_PR',
            defaultValue: true,
            description: 'Create a GitHub Pull Request for GitOps manifest updates instead of pushing directly to the target branch.'
        )
        string(
            name: 'DEPENDENCY_CHECK_DATA_DIR',
            defaultValue: '/var/jenkins_home/caches/dependency-check',
            description: 'Persistent host/cache directory for Dependency Check data. Override this if your Jenkins agent uses a different mounted cache path.'
        )
    }

    environment {
        NODE_IMAGE = 'node:20-alpine'
        TRIVY_IMAGE = 'aquasec/trivy:0.50.1'
        SONAR_SCANNER_IMAGE = 'sonarsource/sonar-scanner-cli:latest'
        BUILT_SERVICES = ''
    }

    stages {
        stage('Checkout & Initialize Config') {
            steps {
                echo '>>> Checking out source code...'
                checkout scm

                script {
                    cfg = docvaultConfig()

                    if (!cfg) {
                        error('docvaultConfig() returned null/empty config.')
                    }

                    cfg.gitOpsBranch = params.GITOPS_BRANCH?.trim()
                        ? params.GITOPS_BRANCH.trim()
                        : cfg.gitOpsBranch

                    cfg.releaseBranch = params.RELEASE_BRANCH?.trim()
                        ? params.RELEASE_BRANCH.trim()
                        : cfg.releaseBranch

                    cfg.registryHost = params.REGISTRY_HOST?.trim()
                        ? params.REGISTRY_HOST.trim()
                        : cfg.registryHost

                    cfg.registryNamespace = params.REGISTRY_NAMESPACE?.trim()
                        ? params.REGISTRY_NAMESPACE.trim()
                        : cfg.registryNamespace

                    cfg.registryCredentialId = params.REGISTRY_CREDENTIAL_ID?.trim()
                        ? params.REGISTRY_CREDENTIAL_ID.trim()
                        : cfg.registryCredentialId

                    cfg.registryCredentialType = params.REGISTRY_CREDENTIAL_TYPE?.trim()
                        ? params.REGISTRY_CREDENTIAL_TYPE.trim()
                        : cfg.registryCredentialType

                    cfg.registryUsername = params.REGISTRY_USERNAME?.trim()
                        ? params.REGISTRY_USERNAME.trim()
                        : cfg.registryUsername

                    cfg.pushLatest = params.PUSH_LATEST
                    cfg.registryBuildCache = params.REGISTRY_BUILD_CACHE
                    cfg.registryBuildCacheSuffix = params.REGISTRY_BUILD_CACHE_SUFFIX?.trim()
                        ? params.REGISTRY_BUILD_CACHE_SUFFIX.trim()
                        : cfg.registryBuildCacheSuffix
                    cfg.alpineSecurityRefresh = params.ALPINE_SECURITY_REFRESH?.trim()
                        ? params.ALPINE_SECURITY_REFRESH.trim()
                        : cfg.alpineSecurityRefresh
                    cfg.signImages = params.SIGN_IMAGES
                    cfg.cosignKeyCredentialId = params.COSIGN_KEY_CREDENTIAL_ID?.trim()
                        ? params.COSIGN_KEY_CREDENTIAL_ID.trim()
                        : cfg.cosignKeyCredentialId
                    cfg.cosignPasswordCredentialId = params.COSIGN_PASSWORD_CREDENTIAL_ID?.trim()
                        ? params.COSIGN_PASSWORD_CREDENTIAL_ID.trim()
                        : cfg.cosignPasswordCredentialId
                    cfg.cosignPublicKeyCredentialId = params.COSIGN_PUBLIC_KEY_CREDENTIAL_ID?.trim()
                        ? params.COSIGN_PUBLIC_KEY_CREDENTIAL_ID.trim()
                        : ''
                    cfg.cosignTlogUpload = params.COSIGN_TLOG_UPLOAD

                    cfg.deployTargetUrl = params.DEPLOY_TARGET_URL?.trim()
                        ? params.DEPLOY_TARGET_URL.trim()
                        : cfg.deployTargetUrl

                    cfg.zapTarget = params.ZAP_TARGET?.trim()
                        ? params.ZAP_TARGET.trim()
                        : (cfg.zapTarget ?: cfg.deployTargetUrl)

                    cfg.runArgoHealthCheck = params.RUN_ARGO_HEALTH_CHECK
                    cfg.argocdNamespace = params.ARGOCD_NAMESPACE?.trim()
                        ? params.ARGOCD_NAMESPACE.trim()
                        : cfg.argocdNamespace
                    cfg.argocdApps = params.ARGOCD_APPS?.trim()
                        ? params.ARGOCD_APPS.trim().split(/[\s,]+/).findAll { it }
                        : cfg.argocdApps
                    cfg.argocdTimeoutSeconds = params.ARGOCD_TIMEOUT_SECONDS?.trim()
                        ? params.ARGOCD_TIMEOUT_SECONDS.trim()
                        : cfg.argocdTimeoutSeconds
                    cfg.kubeconfigCredentialId = params.KUBECONFIG_CREDENTIAL_ID?.trim()
                        ? params.KUBECONFIG_CREDENTIAL_ID.trim()
                        : cfg.kubeconfigCredentialId
                    cfg.createGitOpsPr = params.CREATE_GITOPS_PR != null ? params.CREATE_GITOPS_PR : true

                    cfg.useNvdKey = true
                    cfg.dependencyCheckNoUpdate = params.DEPENDENCY_CHECK_NO_UPDATE
                    cfg.dependencyCheckDataDir = params.DEPENDENCY_CHECK_DATA_DIR?.trim()

                    def resolvedBranchName = env.BRANCH_NAME?.trim()
                    if (!resolvedBranchName && env.GIT_BRANCH?.trim()) {
                        resolvedBranchName = env.GIT_BRANCH.trim().replaceFirst(/^origin\//, '')
                    }
                    if (!resolvedBranchName) {
                        resolvedBranchName = sh(
                            script: 'git branch --show-current || true',
                            returnStdout: true
                        ).trim()
                    }

                    cfg.branchName = resolvedBranchName ?: '(unknown)'
                    cfg.isPullRequest = env.CHANGE_ID?.trim() ? true : false
                    cfg.isReleaseBuild = !cfg.isPullRequest && cfg.branchName == cfg.releaseBranch
                    cfg.imageTag = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()

                    env.IS_PULL_REQUEST = cfg.isPullRequest ? 'true' : 'false'
                    env.IS_RELEASE_BUILD = cfg.isReleaseBuild ? 'true' : 'false'
                    env.IMAGE_TAG = cfg.imageTag

                    echo ">>> Branch name: ${cfg.branchName}"
                    echo ">>> Change request: ${env.CHANGE_ID ?: '(none)'}"
                    echo ">>> Change target: ${env.CHANGE_TARGET ?: '(none)'}"
                    echo ">>> Release branch: ${cfg.releaseBranch}"
                    echo ">>> Pipeline mode: ${cfg.isReleaseBuild ? 'CD release' : 'CI validation'}"
                    echo ">>> Image tag: ${cfg.imageTag}"
                    echo ">>> Effective GitOps branch: ${cfg.gitOpsBranch}"
                    echo ">>> Registry host: ${cfg.registryHost ?: '(Docker Hub default)'}"
                    echo ">>> Registry namespace/project: ${cfg.registryNamespace}"
                    echo ">>> Registry credential ID: ${cfg.registryCredentialId}"
                    echo ">>> Registry credential type: ${cfg.registryCredentialType}"
                    echo ">>> Registry username: ${cfg.registryUsername ?: '(credential-provided)'}"
                    echo ">>> PUSH_LATEST=${params.PUSH_LATEST}"
                    echo ">>> REGISTRY_BUILD_CACHE=${params.REGISTRY_BUILD_CACHE}"
                    echo ">>> REGISTRY_BUILD_CACHE_SUFFIX=${cfg.registryBuildCacheSuffix}"
                    echo ">>> ALPINE_SECURITY_REFRESH=${cfg.alpineSecurityRefresh}"
                    echo ">>> SIGN_IMAGES=${params.SIGN_IMAGES}"
                    echo ">>> COSIGN_KEY_CREDENTIAL_ID=${cfg.cosignKeyCredentialId ?: '(not set)'}"
                    echo ">>> COSIGN_PASSWORD_CREDENTIAL_ID=${cfg.cosignPasswordCredentialId ?: '(not set)'}"
                    echo ">>> COSIGN_PUBLIC_KEY_CREDENTIAL_ID=${cfg.cosignPublicKeyCredentialId ?: '(not set)'}"
                    echo ">>> COSIGN_TLOG_UPLOAD=${params.COSIGN_TLOG_UPLOAD}"
                    echo ">>> FORCE_BUILD_ALL=${params.FORCE_BUILD_ALL}"
                    echo ">>> DEPLOY_TARGET_URL=${cfg.deployTargetUrl ?: '(not set)'}"
                    echo ">>> RUN_ARGO_HEALTH_CHECK=${params.RUN_ARGO_HEALTH_CHECK}"
                    echo ">>> ARGOCD_NAMESPACE=${cfg.argocdNamespace}"
                    echo ">>> ARGOCD_APPS=${cfg.argocdApps.join(',')}"
                    echo ">>> ARGOCD_TIMEOUT_SECONDS=${cfg.argocdTimeoutSeconds}"
                    echo ">>> KUBECONFIG_CREDENTIAL_ID=${cfg.kubeconfigCredentialId ?: '(not set)'}"
                    echo ">>> RUN_ZAP=${params.RUN_ZAP}"
                    echo ">>> ZAP_TARGET=${cfg.zapTarget ?: '(not set)'}"
                    echo ">>> USE_NVD_KEY=${cfg.useNvdKey} (forced)"
                    echo ">>> DEPENDENCY_CHECK_NO_UPDATE=${cfg.dependencyCheckNoUpdate}"
                    echo ">>> ALLOW_DEPENDENCY_CHECK_FAILURE=${params.ALLOW_DEPENDENCY_CHECK_FAILURE}"
                    echo ">>> DEPENDENCY_CHECK_DATA_DIR=${cfg.dependencyCheckDataDir ?: '(default)'}"
                }
            }
        }

        stage('Prevent Loop') {
            steps {
                script {
                    preventLoop()
                }
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    changeSet = detectChanges(cfg)

                    cfg.changeDetectionReady = true
                    cfg.changeDiffRange = changeSet.diffRange ?: ''
                    cfg.changedFiles = changeSet.changedFiles ?: []
                    cfg.forceBuildAll = changeSet.forceBuildAll

                    env.FORCE_BUILD_ALL_EFFECTIVE = changeSet.forceBuildAll ? 'true' : 'false'
                    env.DOCS_ONLY = changeSet.docsOnly ? 'true' : 'false'
                    env.APP_CHANGED = changeSet.appChanged ? 'true' : 'false'
                    env.IAC_CHANGED = changeSet.infraChanged ? 'true' : 'false'
                    env.INFRA_CHANGED = changeSet.gitOpsInfraChanged ? 'true' : 'false'
                    env.PIPELINE_CHANGED = changeSet.pipelineChanged ? 'true' : 'false'
                    env.UNKNOWN_CHANGED = changeSet.unknownChanged ? 'true' : 'false'
                    env.RUN_APP_CI = changeSet.runAppCi ? 'true' : 'false'
                    env.RUN_SECURITY_CI = changeSet.runSecurityCi ? 'true' : 'false'
                    env.RUN_IAC_CI = changeSet.runIacCi ? 'true' : 'false'
                    env.RUN_IMAGE_BUILD = changeSet.runImageBuild ? 'true' : 'false'
                    env.CHANGED_FILES_COUNT = "${changeSet.changedFiles?.size() ?: 0}"
                }
            }
        }

        stage('System Check') {
            steps {
                script {
                    systemCheck()
                }
            }
        }

        stage('Install') {
            when {
                expression {
                    return env.IS_RELEASE_BUILD != 'true' && env.RUN_APP_CI == 'true'
                }
            }
            steps {
                script {
                    installStep(cfg)
                }
            }
        }

        stage('Secret Scan') {
            when {
                expression {
                    return env.IS_RELEASE_BUILD != 'true'
                }
            }
            steps {
                script {
                    echo '>>> Entering Secret Scan stage...'
                    secretScan()
                    echo '>>> Secret Scan stage completed.'
                }
            }
        }

        stage('Pre-build Quality') {
            when {
                expression {
                    return env.IS_RELEASE_BUILD != 'true' && env.RUN_APP_CI == 'true'
                }
            }
            stages {
                stage('Lint & Unit Tests') {
                    parallel {
                        stage('Unit Tests') {
                            steps {
                                script {
                                    unitTests(cfg)
                                }
                            }
                        }

                        stage('Lint') {
                            steps {
                                script {
                                    runPnpmTask(cfg, 'lint')
                                }
                            }
                        }
                    }
                }

                stage('Workspace Build') {
                    steps {
                        script {
                            runPnpmTask(cfg, 'build')
                        }
                    }
                }
            }
        }

        stage('Pre-build Security Gates') {
            when {
                expression {
                    return env.IS_RELEASE_BUILD != 'true' && (env.RUN_SECURITY_CI == 'true' || env.RUN_IAC_CI == 'true')
                }
            }
            parallel {
                stage('SCA - Dependency Check') {
                    when {
                        expression {
                            return env.RUN_SECURITY_CI == 'true' && env.RUN_APP_CI == 'true'
                        }
                    }
                    steps {
                        script {
                            if (params.ALLOW_DEPENDENCY_CHECK_FAILURE) {
                                catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                                    dependencyCheck(cfg)
                                }
                            } else {
                                dependencyCheck(cfg)
                            }
                        }
                    }
                }

                stage('Trivy FS Scan') {
                    when {
                        expression {
                            return env.RUN_SECURITY_CI == 'true'
                        }
                    }
                    steps {
                        script {
                            trivyFsScan(cfg)
                        }
                    }
                }

                stage('SAST - SonarQube') {
                    when {
                        expression {
                            return env.RUN_SECURITY_CI == 'true' && env.RUN_APP_CI == 'true'
                        }
                    }
                    steps {
                        script {
                            sonarSast(cfg + [enforceQualityGate: params.ENFORCE_SONAR_QG])
                        }
                    }
                }

                stage('Pre-build Security - IaC') {
                    when {
                        expression {
                            return env.RUN_IAC_CI == 'true'
                        }
                    }
                    steps {
                        script {
                            policyAsCode(cfg)
                            iacCheckov(cfg)
                            terraformValidate(cfg)
                        }
                    }
                }
            }
        }

        stage('Build & Scan Services') {
            when {
                expression {
                    return env.RUN_IMAGE_BUILD == 'true'
                }
            }
            steps {
                script {
                    def built = buildAndScan(cfg)

                    echo ">>> buildAndScan returned: ${built}"
                    echo ">>> buildAndScan type: ${built?.getClass()?.name}"

                    if (built instanceof Collection) {
                        builtServicesCsv = built
                            .findAll { it }
                            .collect { it.toString().trim() }
                            .findAll { it }
                            .unique()
                            .join(',')
                    } else if (built != null) {
                        def raw = built.toString().trim()
                        builtServicesCsv = (raw == 'null' || raw == '[]') ? '' : raw
                    } else {
                        builtServicesCsv = ''
                    }

                    env.BUILT_SERVICES = builtServicesCsv

                    echo ">>> Normalized builtServicesCsv='${builtServicesCsv}'"
                    echo ">>> Normalized env.BUILT_SERVICES='${env.BUILT_SERVICES}'"
                    echo ">>> BUILT_SERVICES length=${builtServicesCsv.length()}"
                }
            }
        }

        stage('Push & GitOps') {
            when {
                expression {
                    return env.IS_RELEASE_BUILD == 'true' && (builtServicesCsv?.trim() || env.INFRA_CHANGED == 'true')
                }
            }
            steps {
                script {
                    echo ">>> Push & GitOps with builtServicesCsv='${builtServicesCsv}', INFRA_CHANGED='${env.INFRA_CHANGED}'"
                    pushAndGitOps(cfg, builtServicesCsv)
                }
            }
        }

        stage('Argo CD Health Check') {
            when {
                expression {
                    return env.IS_RELEASE_BUILD == 'true' && params.RUN_ARGO_HEALTH_CHECK
                }
            }
            steps {
                script {
                    argocdHealthCheck(cfg)
                }
            }
        }

        stage('Post-deploy Smoke Test') {
            when {
                expression {
                    return env.IS_RELEASE_BUILD == 'true' && (cfg.deployTargetUrl?.trim() ? true : false)
                }
            }
            steps {
                script {
                    postDeploySmokeTest(cfg)
                }
            }
        }

        stage('DAST - OWASP ZAP') {
            when {
                expression {
                    return env.IS_RELEASE_BUILD == 'true' && params.RUN_ZAP
                }
            }
            steps {
                script {
                    echo '>>> Running DAST - OWASP ZAP...'
                    dastZap(cfg)
                }
            }
        }
    }

    post {
        always {
            script {
                postCleanup()
            }
        }
    }
}

def runPnpmTask(cfg, String taskName) {
    def allowedTasks = ['lint', 'build']
    if (!allowedTasks.contains(taskName)) {
        error("Unsupported pnpm task '${taskName}'. Allowed tasks: ${allowedTasks.join(', ')}")
    }

    echo ">>> Running pnpm ${taskName}..."
    def pnpmStoreVolume = cfg.pnpmStoreVolume ?: 'docvault-pnpm-store'
    def turboCacheVolume = cfg.turboCacheVolume ?: 'docvault-turbo-cache'

    sh """
        set -eu
        docker volume create '${pnpmStoreVolume}' >/dev/null
        docker volume create '${turboCacheVolume}' >/dev/null
        docker run --rm \\
            --network host \\
            -e TURBO_CACHE_DIR=/app/.turbo \\
            -v ${env.WORKSPACE}:/app \\
            -v ${pnpmStoreVolume}:/pnpm/store \\
            -v ${turboCacheVolume}:/app/.turbo \\
            -w /app \\
            ${cfg.nodeImage} \\
            sh -c "corepack enable && pnpm config set store-dir /pnpm/store && pnpm ${taskName}"
    """
}
