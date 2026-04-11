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
        booleanParam(name: 'BUILD_DOCKER_IMAGES', defaultValue: true, description: 'Build Docker images for all backend services')
        booleanParam(name: 'PUSH_DOCKER_IMAGES', defaultValue: true, description: 'Push Docker images to Docker Hub')
        booleanParam(name: 'DEPLOY_SERVICES', defaultValue: true, description: 'Deploy backend services with docker compose')
    }

    environment {
        MAVEN_CMD = 'mvn -B -ntp'
        DOCKERHUB_CREDENTIALS_ID = 'dockerhub-credentials'
        DOCKERHUB_USERNAME = 'asynchronouskrishna'
        DOCKERHUB_REPOSITORY = 'quantitymeasurementapp'
        SONARQUBE_SERVER = 'sonarqube-server'
        EMAIL_RECIPIENTS = 'devops@example.com'
        BACKEND_SERVICES = 'eureka-server admin-server measurement-service user-service email-service payment-service api-gateway'
        IMAGE_TAG = "${BUILD_NUMBER}"
        COMPOSE_PROJECT_NAME = 'quantity-measurement'
    }

    stages {
        stage('Checkout') {
            options {
                retry(2)
            }
            steps {
                cleanWs()
                // Use Jenkins SCM configuration to avoid a redundant git clone definition in the pipeline.
                checkout scm
            }
        }

        stage('Validate Tooling') {
            steps {
                // Fail fast if the Jenkins Windows agent is missing Java, Maven, or Docker.
                bat 'java -version'
                bat 'mvn -version'
                bat 'docker version'
                bat 'docker compose version'
            }
        }

        stage('Test') {
            options {
                retry(2)
            }
            steps {
                // Run unit tests for the Maven parent project and all backend modules.
                bat "${MAVEN_CMD} clean test"
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                }
            }
        }

        stage('Build') {
            options {
                retry(2)
            }
            steps {
                // Package all Spring Boot services after tests have already passed.
                bat "${MAVEN_CMD} package -DskipTests"
            }
            post {
                success {
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                }
            }
        }

        stage('SonarQube') {
            when {
                expression { return params.RUN_SONARQUBE }
            }
            steps {
                // Placeholder for SonarQube integration. Configure the named server in Jenkins before enabling.
                withSonarQubeEnv("${SONARQUBE_SERVER}") {
                    bat "${MAVEN_CMD} sonar:sonar"
                }
            }
        }

        stage('Docker Build') {
            when {
                expression { return params.BUILD_DOCKER_IMAGES || params.PUSH_DOCKER_IMAGES || params.DEPLOY_SERVICES }
            }
            options {
                retry(2)
            }
            steps {
                script {
                    // Build each backend image with both an immutable build tag and a rolling latest tag.
                    env.BACKEND_SERVICES.tokenize(' ').each { service ->
                        bat """
                            docker build -f ${service}\\Dockerfile ^
                              -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-${env.IMAGE_TAG} ^
                              -t ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-latest .
                        """.stripIndent().trim()
                    }
                }
            }
        }

        stage('Docker Login') {
            when {
                expression { return params.PUSH_DOCKER_IMAGES || params.DEPLOY_SERVICES }
            }
            steps {
                // Authenticate securely to Docker Hub with Jenkins-managed credentials.
                withCredentials([usernamePassword(
                    credentialsId: env.DOCKERHUB_CREDENTIALS_ID,
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    bat '''
@echo off
echo %DOCKER_PASS%| docker login -u %DOCKER_USER% --password-stdin
'''
                }
            }
        }

        stage('Docker Push') {
            when {
                expression { return params.PUSH_DOCKER_IMAGES }
            }
            options {
                retry(2)
            }
            steps {
                script {
                    // Push both immutable and latest tags so deployments can choose stable or rolling releases.
                    env.BACKEND_SERVICES.tokenize(' ').each { service ->
                        bat "docker push ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-${env.IMAGE_TAG}"
                        bat "docker push ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}:${service}-latest"
                    }
                }
            }
        }

        stage('Deploy') {
            when {
                expression { return params.DEPLOY_SERVICES }
            }
            options {
                retry(2)
            }
            steps {
                // Pull the tagged backend images and recreate only the backend services.
                bat '''
@echo off
setlocal
set DOCKERHUB_USERNAME=%DOCKERHUB_USERNAME%
set DOCKERHUB_REPOSITORY=%DOCKERHUB_REPOSITORY%
set IMAGE_TAG=%IMAGE_TAG%
set COMPOSE_PROJECT_NAME=%COMPOSE_PROJECT_NAME%

echo Attempting to stop any existing backend deployment...
docker compose -p %COMPOSE_PROJECT_NAME% down --remove-orphans
if errorlevel 1 (
    echo No existing deployment was running or docker compose down returned a non-fatal error. Continuing...
)

echo Pulling backend images for tag %IMAGE_TAG%...
docker compose -p %COMPOSE_PROJECT_NAME% pull eureka-server admin-server measurement-service user-service email-service payment-service api-gateway
if errorlevel 1 exit /b 1

echo Starting backend services...
docker compose -p %COMPOSE_PROJECT_NAME% up -d --remove-orphans eureka-server admin-server measurement-service user-service email-service payment-service api-gateway
if errorlevel 1 exit /b 1

endlocal
'''
            }
        }
    }

    post {
        success {
            emailext(
                to: "${EMAIL_RECIPIENTS}",
                subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Build and deployment completed successfully.

Job: ${env.JOB_NAME}
Build Number: ${env.BUILD_NUMBER}
Build URL: ${env.BUILD_URL}
Docker Repository: ${env.DOCKERHUB_USERNAME}/${env.DOCKERHUB_REPOSITORY}
Image Tag: ${env.IMAGE_TAG}
"""
            )
        }

        failure {
            emailext(
                to: "${EMAIL_RECIPIENTS}",
                subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Build or deployment failed.

Job: ${env.JOB_NAME}
Build Number: ${env.BUILD_NUMBER}
Build URL: ${env.BUILD_URL}

Review the Jenkins console log for the failed stage.
"""
            )
        }

        always {
            bat '''
@echo off
docker logout >nul 2>&1
exit /b 0
'''
            cleanWs(deleteDirs: true, notFailBuild: true)
        }
    }
}
