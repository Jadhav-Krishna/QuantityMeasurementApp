pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
    }

    parameters {
        booleanParam(name: 'RUN_SONARQUBE', defaultValue: false, description: 'Run SonarQube analysis')
        booleanParam(name: 'BUILD_DOCKER_IMAGES', defaultValue: true, description: 'Build Docker images')
        booleanParam(name: 'PUSH_DOCKER_IMAGES', defaultValue: true, description: 'Push Docker images to Docker Hub')
        booleanParam(name: 'DEPLOY_TO_EC2', defaultValue: false, description: 'SSH into EC2 and redeploy all services')
        string(name: 'SONAR_HOST_URL_OVERRIDE', defaultValue: '', description: 'Optional SonarQube URL override, for example http://<host>:9000')
    }

    environment {
        MAVEN_CMD = 'mvn -B -ntp'
        DOCKERHUB_USERNAME = 'asynchronouskrishna'
        DOCKERHUB_REPOSITORY = 'quantitymeasurementapp'
        DOCKERHUB_ACCESS_TOKEN = 'dckr_pat_T7h8xSY8FTnYMGC79RWhBk4EZ70'
        SONARQUBE_SERVER = 'sonarqube-server'
        EMAIL_RECIPIENTS = 'kj4241808@gmail.com'
        BACKEND_SERVICES = 'eureka-server admin-server measurement-service user-service email-service payment-service api-gateway'
        IMAGE_TAG = "${BUILD_NUMBER}"
        COMPOSE_PROJECT_NAME = 'quantity-measurement'
        EC2_USER = 'ubuntu'
        EC2_HOST = 'ec2-13-126-227-51.ap-south-1.compute.amazonaws.com'
        EC2_KEY_FILE = 'C:\\Users\\krish\\.ssh\\QuantityMeasurementApp.pem'
        EC2_APP_DIR = '~/app'
        BACKEND_REPO_URL = 'https://github.com/Jadhav-Krishna/QuantityMeasurementApp.git'
        BACKEND_REPO_BRANCH = 'feature/Deployment'
        BACKEND_REPO_DIR = 'QuantityMeasurementApp'
        FRONTEND_REPO_URL = 'https://github.com/Jadhav-Krishna/QuantityMeasurementApp-Frontend.git'
        FRONTEND_REPO_BRANCH = 'feature/frontend-react'
        FRONTEND_REPO_DIR = 'QuantityMeasurementApp-Frontend'
    }

    stages {
        stage('Prepare EC2 Workspace') {
            options { retry(2) }
            steps {
                bat """
@echo off
ssh -i "${env.EC2_KEY_FILE}" -o StrictHostKeyChecking=no ${env.EC2_USER}@${env.EC2_HOST} "mkdir -p ${env.EC2_APP_DIR} && cd ${env.EC2_APP_DIR} && rm -rf ${env.BACKEND_REPO_DIR} ${env.FRONTEND_REPO_DIR} && git clone --branch ${env.BACKEND_REPO_BRANCH} --single-branch ${env.BACKEND_REPO_URL} ${env.BACKEND_REPO_DIR} && git clone --branch ${env.FRONTEND_REPO_BRANCH} --single-branch ${env.FRONTEND_REPO_URL} ${env.FRONTEND_REPO_DIR}"
"""
            }
        }

        stage('Install EC2 Tooling') {
            options { retry(2) }
            steps {
                bat """
@echo off
ssh -i "${env.EC2_KEY_FILE}" -o StrictHostKeyChecking=no ${env.EC2_USER}@${env.EC2_HOST} "sudo apt-get update && sudo apt-get install -y openjdk-21-jdk maven git curl ca-certificates psmisc && sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin && sudo systemctl enable docker && sudo systemctl start docker && sudo usermod -aG docker ${env.EC2_USER}"
"""
            }
        }

        stage('Validate EC2 Tooling') {
            steps {
                bat """
@echo off
ssh -i "${env.EC2_KEY_FILE}" -o StrictHostKeyChecking=no ${env.EC2_USER}@${env.EC2_HOST} "java -version && mvn -version && docker version && docker compose version"
"""
            }
        }

        stage('Test') {
            options { retry(2) }
            steps {
                bat """
@echo off
ssh -i "${env.EC2_KEY_FILE}" -o StrictHostKeyChecking=no ${env.EC2_USER}@${env.EC2_HOST} "cd ${env.EC2_APP_DIR}/${env.BACKEND_REPO_DIR} && ${env.MAVEN_CMD} clean test"
"""
            }
        }

        stage('Build') {
            options { retry(2) }
            steps {
                bat """
@echo off
ssh -i "${env.EC2_KEY_FILE}" -o StrictHostKeyChecking=no ${env.EC2_USER}@${env.EC2_HOST} "cd ${env.EC2_APP_DIR}/${env.BACKEND_REPO_DIR} && ${env.MAVEN_CMD} package -DskipTests"
"""
            }
        }

        stage('SonarQube') {
            when { expression { params.RUN_SONARQUBE } }
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                    timeout(time: 10, unit: 'MINUTES') {
                        script {
                            def sonarHostUrl = params.SONAR_HOST_URL_OVERRIDE?.trim()
                            if (!sonarHostUrl) {
                                sonarHostUrl = 'http://localhost:9000'
                            }

                            bat """
@echo off
ssh -i "${env.EC2_KEY_FILE}" -o StrictHostKeyChecking=no ${env.EC2_USER}@${env.EC2_HOST} "curl -fsS ${sonarHostUrl}/api/system/status >/dev/null 2>&1 || { echo SonarQube server is unreachable at ${sonarHostUrl}; exit 0; }; cd ${env.EC2_APP_DIR}/${env.BACKEND_REPO_DIR} && ${env.MAVEN_CMD} verify -DskipTests sonar:sonar -Dsonar.host.url=${sonarHostUrl}"
"""
                        }
                    }
                }
            }
        }

        stage('Docker Build') {
            when {
                expression { params.BUILD_DOCKER_IMAGES || params.PUSH_DOCKER_IMAGES || params.DEPLOY_TO_EC2 }
            }
            options { retry(2) }
            steps {
                script {
                    def buildCommands = env.BACKEND_SERVICES.tokenize(' ').collect { service ->
                        "docker build -f ${service}/Dockerfile -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-${env.IMAGE_TAG} -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-latest ."
                    }
                    buildCommands << "docker build --build-arg VITE_API_BASE_URL='' --build-arg VITE_RAZORPAY_KEY_ID='' -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:frontend-${env.IMAGE_TAG} -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:frontend-latest ../${env.FRONTEND_REPO_DIR}"

                    bat """
                        @echo off
                        ssh -i "${env.EC2_KEY_FILE}" -o StrictHostKeyChecking=no ${env.EC2_USER}@${env.EC2_HOST} "cd ${env.EC2_APP_DIR}/${env.BACKEND_REPO_DIR} && ${buildCommands.join(' && ')}"
                    """
                }
            }
        }

        stage('Docker Login') {
            when {
                expression { params.PUSH_DOCKER_IMAGES || params.DEPLOY_TO_EC2 }
            }
            steps {
                bat """
@echo off
ssh -i "${env.EC2_KEY_FILE}" -o StrictHostKeyChecking=no ${env.EC2_USER}@${env.EC2_HOST} "docker logout >/dev/null 2>&1 || true && docker login -u ${env.DOCKERHUB_USERNAME} -p ${env.DOCKERHUB_ACCESS_TOKEN}"
"""
            }
        }

        stage('Docker Push') {
            when {
                expression { params.PUSH_DOCKER_IMAGES || params.DEPLOY_TO_EC2 }
            }
            options { retry(2) }
            steps {
                script {
                    def pushCommands = []
                    env.BACKEND_SERVICES.tokenize(' ').each { service ->
                        pushCommands << "docker push ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-${env.IMAGE_TAG}"
                        pushCommands << "docker push ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-latest"
                    }
                    pushCommands << "docker push ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:frontend-${env.IMAGE_TAG}"
                    pushCommands << "docker push ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:frontend-latest"

                    bat """
                        @echo off
                        ssh -i "${env.EC2_KEY_FILE}" -o StrictHostKeyChecking=no ${env.EC2_USER}@${env.EC2_HOST} "${pushCommands.join(' && ')}"
                    """
                }
            }
        }

        stage('Deploy to EC2') {
            when { expression { params.DEPLOY_TO_EC2 } }
            steps {
                bat """
@echo off
ssh -i "${env.EC2_KEY_FILE}" -o StrictHostKeyChecking=no ${env.EC2_USER}@${env.EC2_HOST} "cp ${env.EC2_APP_DIR}/${env.BACKEND_REPO_DIR}/docker-compose.yml ${env.EC2_APP_DIR}/docker-compose.yml && cd ${env.EC2_APP_DIR} && export IMAGE_TAG=${env.IMAGE_TAG} && export DOCKERHUB_USERNAME=${env.DOCKERHUB_USERNAME} && export DOCKERHUB_REPOSITORY=${env.DOCKERHUB_REPOSITORY} && docker compose -p ${env.COMPOSE_PROJECT_NAME} down --remove-orphans || true && sudo fuser -k 80/tcp 8080/tcp 8081/tcp 8082/tcp 8083/tcp 8084/tcp 8761/tcp 9000/tcp 9090/tcp || true && docker ps -aq --filter publish=80 --filter publish=8080 --filter publish=8081 --filter publish=8082 --filter publish=8083 --filter publish=8084 --filter publish=8761 --filter publish=9000 --filter publish=9090 | xargs -r docker rm -f && docker compose -p ${env.COMPOSE_PROJECT_NAME} pull && docker compose -p ${env.COMPOSE_PROJECT_NAME} up -d --remove-orphans"
"""
            }
        }
    }

    post {
        success {
            emailext(
                to: "${env.EMAIL_RECIPIENTS}",
                subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Build completed successfully.

Job:        ${env.JOB_NAME}
Build:      ${env.BUILD_NUMBER}
URL:        ${env.BUILD_URL}
Image Tag:  ${env.IMAGE_TAG}
Repository: ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}
"""
            )
        }
        failure {
            emailext(
                to: "${env.EMAIL_RECIPIENTS}",
                subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Build or deployment failed.

Job:   ${env.JOB_NAME}
Build: ${env.BUILD_NUMBER}
URL:   ${env.BUILD_URL}

Check the Jenkins console log for the failed stage.
"""
            )
        }
        always {
            script {
                if (env.WORKSPACE) {
                    cleanWs(deleteDirs: true, notFailBuild: true)
                }
            }
        }
    }
}
