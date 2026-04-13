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
        DOCKERHUB_CREDENTIALS_ID = 'dockerhub-credentials'
        DOCKERHUB_USERNAME = 'asynchronouskrishna'
        DOCKERHUB_REPOSITORY = 'quantitymeasurementapp'
        SONARQUBE_SERVER = 'sonarqube-server'
        EMAIL_RECIPIENTS = 'kj4241808@gmail.com'
        BACKEND_SERVICES = 'eureka-server admin-server measurement-service user-service email-service payment-service api-gateway'
        IMAGE_TAG = "${BUILD_NUMBER}"
        COMPOSE_PROJECT_NAME = 'quantity-measurement'
        // Jenkins credential IDs - create these in Jenkins > Credentials.
        // ec2-host: Secret text containing only the hostname, for example
        // ec2-65-2-129-136.ap-south-1.compute.amazonaws.com
        // ec2-ssh-key: SSH Username with private key using the QuantityMeasurementApp.pem key for user ubuntu
        EC2_SSH_CREDENTIALS_ID = 'ec2-ssh-key'
        EC2_USER = 'ubuntu'
        EC2_HOST = 'ec2-13-126-227-51.ap-south-1.compute.amazonaws.com'
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
                sshagent(credentials: ["${EC2_SSH_CREDENTIALS_ID}"]) {
                    bat """
@echo off
ssh -o StrictHostKeyChecking=no %EC2_USER%@${env.EC2_HOST} "mkdir -p ${env.EC2_APP_DIR} && cd ${env.EC2_APP_DIR} && rm -rf ${env.BACKEND_REPO_DIR} ${env.FRONTEND_REPO_DIR} && git clone --branch ${env.BACKEND_REPO_BRANCH} --single-branch ${env.BACKEND_REPO_URL} ${env.BACKEND_REPO_DIR} && git clone --branch ${env.FRONTEND_REPO_BRANCH} --single-branch ${env.FRONTEND_REPO_URL} ${env.FRONTEND_REPO_DIR}"
"""
                }
            }
        }

        stage('Validate EC2 Tooling') {
            steps {
                sshagent(credentials: ["${EC2_SSH_CREDENTIALS_ID}"]) {
                    bat """
@echo off
ssh -o StrictHostKeyChecking=no %EC2_USER%@${env.EC2_HOST} "java -version && mvn -version && docker version && docker compose version"
"""
                }
            }
        }

        stage('Test') {
            options { retry(2) }
            steps {
                sshagent(credentials: ["${EC2_SSH_CREDENTIALS_ID}"]) {
                    bat """
@echo off
ssh -o StrictHostKeyChecking=no %EC2_USER%@${env.EC2_HOST} "cd ${env.EC2_APP_DIR}/${env.BACKEND_REPO_DIR} && ${env.MAVEN_CMD} clean test"
"""
                }
            }
        }

        stage('Build') {
            options { retry(2) }
            steps {
                sshagent(credentials: ["${EC2_SSH_CREDENTIALS_ID}"]) {
                    bat """
@echo off
ssh -o StrictHostKeyChecking=no %EC2_USER%@${env.EC2_HOST} "cd ${env.EC2_APP_DIR}/${env.BACKEND_REPO_DIR} && ${env.MAVEN_CMD} package -DskipTests"
"""
                }
            }
        }

        stage('SonarQube') {
            when { expression { params.RUN_SONARQUBE } }
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE') {
                    timeout(time: 10, unit: 'MINUTES') {
                        sshagent(credentials: ["${EC2_SSH_CREDENTIALS_ID}"]) {
                            script {
                                def sonarHostUrl = params.SONAR_HOST_URL_OVERRIDE?.trim()
                                if (!sonarHostUrl) {
                                    sonarHostUrl = 'http://localhost:9000'
                                }

                                bat """
@echo off
ssh -o StrictHostKeyChecking=no %EC2_USER%@${env.EC2_HOST} "curl -fsS ${sonarHostUrl}/api/system/status >/dev/null 2>&1 || { echo SonarQube server is unreachable at ${sonarHostUrl}; exit 0; }; cd ${env.EC2_APP_DIR}/${env.BACKEND_REPO_DIR} && ${env.MAVEN_CMD} verify -DskipTests sonar:sonar -Dsonar.host.url=${sonarHostUrl}"
"""
                            }
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
                sshagent(credentials: ["${EC2_SSH_CREDENTIALS_ID}"]) {
                    script {
                        def buildCommands = env.BACKEND_SERVICES.tokenize(' ').collect { service ->
                            "docker build -f ${service}/Dockerfile -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-${env.IMAGE_TAG} -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-latest ."
                        }
                        buildCommands << "docker build --build-arg VITE_API_BASE_URL='' --build-arg VITE_RAZORPAY_KEY_ID='' -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:frontend-${env.IMAGE_TAG} -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:frontend-latest ../${env.FRONTEND_REPO_DIR}"

                        bat """
@echo off
ssh -o StrictHostKeyChecking=no %EC2_USER%@${env.EC2_HOST} "cd ${env.EC2_APP_DIR}/${env.BACKEND_REPO_DIR} && ${buildCommands.join(' && ')}"
"""
                    }
                }
            }
        }

        stage('Docker Login') {
            when {
                expression { params.PUSH_DOCKER_IMAGES || params.DEPLOY_TO_EC2 }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: env.DOCKERHUB_CREDENTIALS_ID,
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sshagent(credentials: ["${EC2_SSH_CREDENTIALS_ID}"]) {
                        bat """
@echo off
ssh -o StrictHostKeyChecking=no %EC2_USER%@${env.EC2_HOST} "printf '%s' '${DOCKER_PASS}' | docker login -u '${DOCKER_USER}' --password-stdin"
"""
                        script {
                            env.DOCKERHUB_USERNAME = env.DOCKER_USER
                        }
                    }
                }
            }
        }

        stage('Docker Push') {
            when {
                expression { params.PUSH_DOCKER_IMAGES || params.DEPLOY_TO_EC2 }
            }
            options { retry(2) }
            steps {
                sshagent(credentials: ["${EC2_SSH_CREDENTIALS_ID}"]) {
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
ssh -o StrictHostKeyChecking=no %EC2_USER%@${env.EC2_HOST} "${pushCommands.join(' && ')}"
"""
                    }
                }
            }
        }

        stage('Deploy to EC2') {
            when { expression { params.DEPLOY_TO_EC2 } }
            steps {
                sshagent(credentials: ["${EC2_SSH_CREDENTIALS_ID}"]) {
                    bat """
@echo off
ssh -o StrictHostKeyChecking=no %EC2_USER%@${env.EC2_HOST} "^
  cp ${env.EC2_APP_DIR}/${env.BACKEND_REPO_DIR}/docker-compose.yml ${env.EC2_APP_DIR}/docker-compose.yml && ^
  cd ${env.EC2_APP_DIR} && ^
  export IMAGE_TAG=${env.IMAGE_TAG} && ^
  export DOCKERHUB_USERNAME=${env.DOCKERHUB_USERNAME} && ^
  export DOCKERHUB_REPOSITORY=${env.DOCKERHUB_REPOSITORY} && ^
  docker compose -p ${env.COMPOSE_PROJECT_NAME} pull && ^
  docker compose -p ${env.COMPOSE_PROJECT_NAME} up -d --remove-orphans"
"""
                }
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
