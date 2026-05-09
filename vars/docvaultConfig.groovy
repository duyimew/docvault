def call() {
    def gitOpsBranch = env.GITOPS_BRANCH?.trim() ? env.GITOPS_BRANCH.trim() : 'gitops-testing'
    def sonarHostUrl = env.SONAR_HOST_URL?.trim() ? env.SONAR_HOST_URL.trim() : 'http://sonarqube:9000'
    def zapTarget = env.ZAP_TARGET?.trim() ? env.ZAP_TARGET.trim() : ''

    return [
        agentLabel: 'docker-agent-alpine-ubuntu-vm',
        nodeImage: 'node:20-alpine',
        trivyImage: 'aquasec/trivy:0.70.0',
        sonarScannerImage: 'sonarsource/sonar-scanner-cli:latest',
        sonarQubeInstallation: 'sqdocvault',
        sonarProjectKey: 'docvault',
        sonarHostUrl: sonarHostUrl,
        sonarHostCandidates: [sonarHostUrl, 'http://host.docker.internal:9000', 'http://localhost:9000', 'http://127.0.0.1:9000', 'http://172.17.0.1:9000'],
        sonarDockerRunArgs: '--network host --add-host=host.docker.internal:host-gateway',
        checkovImage: 'bridgecrew/checkov:latest',
        terraformImage: 'hashicorp/terraform:1.8.5',
        terraformDir: 'infra/terraform/aws-eks',
        skipChecks: 'CKV_K8S_43',
        skipPaths: 'infra/k8s/infra-deps',
        dockerOrg: 'daithang59',
        buildParallelism: 3,
        pushParallelism: 3,
        pnpmStoreVolume: 'docvault-pnpm-store',
        services: ['gateway', 'metadata-service', 'document-service', 'notification-service', 'workflow-service', 'audit-service'],
        webAppName: 'web',
        webImageName: 'docvault',
        webDockerfile: 'apps/web/Dockerfile',
        backendDockerfile: 'Dockerfile.backend',
        helmValuesDir: 'infra/k8s/values',
        gitOpsBranch: gitOpsBranch,
        gitOpsRepoUrl: 'https://github.com/daithang59/docvault.git',
        zapTarget: zapTarget
    ]
}
