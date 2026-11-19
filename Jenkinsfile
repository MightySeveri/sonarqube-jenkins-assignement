
pipeline {
    agent any

    environment {
        DOCKER_IMAGE = 'mightyseveri/severi-temperatureconverter-pipeline'

        SONARQUBE_SERVER = 'SonarQubeServer'
        SONAR_TOKEN = credentials('SONAR_TOKEN')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build, Test and Coverage') {
            steps {
                sh 'mvn clean verify'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
                success {
                    archiveArtifacts artifacts: 'target/site/jacoco/**'
                    jacoco(
                        execPattern: 'target/jacoco.exec',
                        classPattern: 'target/classes',
                        sourcePattern: 'src/main/java'
                    )
                }
            }
        }


        stage('SonarQube Analysis') {
            steps {
                script {
                    def scannerHome = tool 'SonarScanner'

                    withSonarQubeEnv("${env.SONARQUBE_SERVER}") {
                        sh """
                            "${scannerHome}/bin/sonar-scanner" \
                            -Dsonar.projectKey=BMI_CL \
                            -Dsonar.sources=src \
                            -Dsonar.projectName=BMI_CL \
                            -Dsonar.host.url=http://localhost:9000 \
                            -Dsonar.java.binaries=target/classes \
                            -Dsonar.token="\$SONAR_TOKEN"
                        """
                    }
                }
            }
        }


        stage('Build Docker Image') {
            steps {
                sh 'docker build -t "$DOCKER_IMAGE:latest" .'
            }
        }

        stage('Push Docker Image to Docker Hub') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        echo "$DOCKER_PASSWORD" | docker login \
                            -u "$DOCKER_USERNAME" \
                            --password-stdin
                        docker push "$DOCKER_IMAGE:latest"
                    '''
                }
            }
        }
    }
}
