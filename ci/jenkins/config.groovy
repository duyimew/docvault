def call() {
    def gitOpsBranch = env.GITOPS_BRANCH?.trim() ? env.GITOPS_BRANCH.trim() : 'testing'

    return [
        agentLabel: 'docker-agent-alpine-ubuntu-vm',
        nodeImage: 'node:20-alpine',
        trivyImage: 'aquasec/trivy:0.50.1',
        sonarScannerImage: 'sonarsource/sonar-scanner-cli:latest',
        sonarQubeInstallation: 'sqdocvault',
        sonarProjectKey: 'docvault',
        sonarHostUrl: 'http://10.0.3.137:9005',
        checkovImage: 'bridgecrew/checkov:latest',
        dockerOrg: 'duyimew',
        services: ['gateway', 'metadata-service', 'document-service', 'notification-service', 'workflow-service', 'audit-service'],
        webAppName: 'web',
        webImageName: 'docvault',
        webDockerfile: 'apps/web/Dockerfile',
        backendDockerfile: 'Dockerfile.backend',
        helmValuesDir: 'infra/k8s/values',
        gitOpsBranch: gitOpsBranch,
        gitOpsRepoUrl: 'https://github.com/duyimew/docvault.git',
        zapTarget: 'http://10.0.3.134:30000/api'
    ]
}

return this
