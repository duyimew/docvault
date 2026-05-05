def call(Map cfg = [:]) {
    echo '>>> Running Terraform fmt/init/validate...'

    String terraformImage = cfg.terraformImage ?: 'hashicorp/terraform:1.8.5'
    String terraformDir   = cfg.terraformDir ?: 'infra/terraform/aws-eks'

    withEnv([
        "TERRAFORM_IMAGE=${terraformImage}",
        "TERRAFORM_DIR=${terraformDir}"
    ]) {
        sh '''
            set -eu

            if [ ! -d "$WORKSPACE/$TERRAFORM_DIR" ]; then
              echo "Terraform directory $TERRAFORM_DIR not found. Skipping."
              exit 0
            fi

            docker run --rm \
              -v "$WORKSPACE:/repo" \
              -w "/repo/$TERRAFORM_DIR" \
              "$TERRAFORM_IMAGE" \
              fmt -check -recursive

            docker run --rm \
              -v "$WORKSPACE:/repo" \
              -w "/repo/$TERRAFORM_DIR" \
              "$TERRAFORM_IMAGE" \
              init -backend=false

            docker run --rm \
              -v "$WORKSPACE:/repo" \
              -w "/repo/$TERRAFORM_DIR" \
              "$TERRAFORM_IMAGE" \
              validate
        '''
    }
}
