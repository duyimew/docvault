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
        booleanParam(
            name: 'RUN_ZAP',
            defaultValue: false,
            description: 'Run DAST scan after deploy target is reachable.'
        )
        string(
            name: 'ZAP_TARGET',
            defaultValue: '',
            description: 'Reachable Gateway API URL for ZAP, for example http://<gateway-url>/api. Required only when RUN_ZAP=true.'
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

                    cfg.zapTarget = params.ZAP_TARGET?.trim()
                        ? params.ZAP_TARGET.trim()
                        : cfg.zapTarget

                    echo ">>> Effective GitOps branch: ${cfg.gitOpsBranch}"
                    echo ">>> FORCE_BUILD_ALL=${params.FORCE_BUILD_ALL}"
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
                            dependencyCheck()
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
                            sonarSast(cfg)
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
