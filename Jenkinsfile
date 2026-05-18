@Library('docvault@devsecops-pipeline') _

def cfg = [:]
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
            defaultValue: false,
            description: 'Use NVD API key for Dependency Check to bypass rate limits (requires "nvd-api-key" credential).'
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

                    cfg.useNvdKey = params.USE_NVD_KEY

                    echo ">>> Effective GitOps branch: ${cfg.gitOpsBranch}"
                    echo ">>> FORCE_BUILD_ALL=${params.FORCE_BUILD_ALL}"
                    echo ">>> DEPLOY_TARGET_URL=${cfg.deployTargetUrl ?: '(not set)'}"
                    echo ">>> RUN_ARGO_HEALTH_CHECK=${params.RUN_ARGO_HEALTH_CHECK}"
                    echo ">>> ARGOCD_NAMESPACE=${cfg.argocdNamespace}"
                    echo ">>> ARGOCD_APPS=${cfg.argocdApps.join(',')}"
                    echo ">>> ARGOCD_TIMEOUT_SECONDS=${cfg.argocdTimeoutSeconds}"
                    echo ">>> KUBECONFIG_CREDENTIAL_ID=${cfg.kubeconfigCredentialId ?: '(not set)'}"
                    echo ">>> RUN_ZAP=${params.RUN_ZAP}"
                    echo ">>> ZAP_TARGET=${cfg.zapTarget ?: '(not set)'}"
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

        stage('System Check') {
            steps {
                script {
                    systemCheck()
                }
            }
        }

        stage('Install') {
            steps {
                script {
                    installStep(cfg)
                }
            }
        }

        stage('Pre-build Security') {
            parallel {
                stage('SCA - Dependency Check') {
                    steps {
                        script {
                            dependencyCheck(cfg)
                        }
                    }
                }

                stage('Trivy FS Scan') {
                    steps {
                        script {
                            trivyFsScan(cfg)
                        }
                    }
                }

                stage('Unit Tests') {
                    steps {
                        script {
                            unitTests(cfg)
                        }
                    }
                }

                stage('SAST - SonarQube') {
                    steps {
                        script {
                            // Enforce Quality Gate for P1.5
                            sonarSast(cfg + [enforceQualityGate: true])
                        }
                    }
                }

                stage('Secret Scan') {
                    steps {
                        script {
                            secretScan()
                        }
                    }
                }

                stage('Policy as Code') {
                    steps {
                        script {
                            policyAsCode(cfg)
                        }
                    }
                }

                stage('IaC - Checkov Scan') {
                    steps {
                        script {
                            iacCheckov(cfg)
                        }
                    }
                }

                stage('IaC - Terraform Validate') {
                    steps {
                        script {
                            terraformValidate(cfg)
                        }
                    }
                }
            }
        }

        stage('Build & Scan Services') {
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
                    return builtServicesCsv?.trim() || env.INFRA_CHANGED == 'true'
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
                    return params.RUN_ARGO_HEALTH_CHECK
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
                    return cfg.deployTargetUrl?.trim() ? true : false
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
                    return params.RUN_ZAP
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
