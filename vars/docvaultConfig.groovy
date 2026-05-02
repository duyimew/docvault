def call() {
    def gitOpsBranch = env.GITOPS_BRANCH?.trim() ? env.GITOPS_BRANCH.trim() : 'gitops-testing'
    def sonarHostUrl = env.SONAR_HOST_URL?.trim() ? env.SONAR_HOST_URL.trim() : 'http://host.docker.internal:9000'

    return [
        agentLabel: 'docker-agent-alpine-ubuntu-vm',
        nodeImage: 'node:20-alpine',
        trivyImage: 'aquasec/trivy:0.70.0',
        sonarScannerImage: 'sonarsource/sonar-scanner-cli:latest',
        sonarQubeInstallation: 'sqdocvault',
        sonarProjectKey: 'docvault',
        sonarHostUrl: sonarHostUrl,
        checkovImage: 'bridgecrew/checkov:latest',
        skipChecks: 'CKV_K8S_43',
        dockerOrg: 'daithang59',
        services: ['gateway', 'metadata-service', 'document-service', 'notification-service', 'workflow-service', 'audit-service'],
        webAppName: 'web',
        webImageName: 'docvault',
        webDockerfile: 'apps/web/Dockerfile',
        backendDockerfile: 'Dockerfile.backend',
        helmValuesDir: 'infra/k8s/values',
        gitOpsBranch: gitOpsBranch,
        gitOpsRepoUrl: 'https://github.com/daithang59/docvault.git',
        zapTarget: 'http://10.0.3.138:30000/api'
    ]
}
