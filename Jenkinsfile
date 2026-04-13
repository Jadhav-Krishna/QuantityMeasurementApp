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
        FRONTEND_REPO_URL = 'https://github.com/asynchronouskrishna/QuantityMeasurementApp-Frontend.git'
        FRONTEND_REPO_DIR = 'QuantityMeasurementApp-Frontend'
    }

    stages {
        stage('Checkout') {
            options { retry(2) }
            steps {
                cleanWs()
                checkout scm
                dir("${FRONTEND_REPO_DIR}") {
                    git url: "${FRONTEND_REPO_URL}", branch: 'main'
                }
            }
        }

        stage('Validate Tooling') {
            steps {
                bat 'java -version'
                bat 'mvn -version'
                bat 'docker version'
                bat 'docker compose version'
            }
        }

        stage('Test') {
            options { retry(2) }
            steps {
                bat "${MAVEN_CMD} clean test"
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                }
            }
        }

        stage('Build') {
            options { retry(2) }
            steps {
                bat "${MAVEN_CMD} package -DskipTests"
            }
            post {
                success {
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                }
            }
        }

        stage('SonarQube') {
            when { expression { params.RUN_SONARQUBE } }
            steps {
                withSonarQubeEnv("${SONARQUBE_SERVER}") {
                    bat "${MAVEN_CMD} sonar:sonar"
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
                    env.BACKEND_SERVICES.tokenize(' ').each { service ->
                        bat """
                            docker build -f ${service}\\Dockerfile ^
                              -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-${env.IMAGE_TAG} ^
                              -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-latest .
                        """.stripIndent().trim()
                    }

                    bat """
                        docker build ^
                          --build-arg VITE_API_BASE_URL="" ^
                          --build-arg VITE_RAZORPAY_KEY_ID="" ^
                          -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:frontend-${env.IMAGE_TAG} ^
                          -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:frontend-latest ^
                          ${env.FRONTEND_REPO_DIR}
                    """.stripIndent().trim()
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
                    bat '@echo off\necho %DOCKER_PASS%| docker login -u %DOCKER_USER% --password-stdin'
                }
            }
        }

        stage('Docker Push') {
            when {
                expression { params.PUSH_DOCKER_IMAGES || params.DEPLOY_TO_EC2 }
            }
            options { retry(2) }
            steps {
                script {
                    env.BACKEND_SERVICES.tokenize(' ').each { service ->
                        bat "docker push ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-${env.IMAGE_TAG}"
                        bat "docker push ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-latest"
                    }
                    bat "docker push ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:frontend-${env.IMAGE_TAG}"
                    bat "docker push ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:frontend-latest"
                }
            }
        }

        stage('Deploy to EC2') {
            when { expression { params.DEPLOY_TO_EC2 } }
            steps {
                withCredentials([string(credentialsId: 'ec2-host', variable: 'EC2_HOST')]) {
                    sshagent(credentials: ["${EC2_SSH_CREDENTIALS_ID}"]) {
                        bat """
@echo off
scp -o StrictHostKeyChecking=no docker-compose.yml %EC2_USER%@%EC2_HOST%:~/app/docker-compose.yml
ssh -o StrictHostKeyChecking=no %EC2_USER%@%EC2_HOST% "^
  cd ~/app && ^
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
                    bat '@echo off\ndocker logout >nul 2>&1\nexit /b 0'
                    cleanWs(deleteDirs: true, notFailBuild: true)
                }
            }
        }
    }
}
