pipeline {
    agent any

    environment {
        // these are the environment variables (Amir's machine) that will be used in the pipeline
       DOCKER_IMAGE = 'mightyseveri/severi-temperatureconverter-pipeline'


        SONARQUBE_SERVER = 'http://localhost:9000'
        SONAR_TOKEN = credentials('SONAR_TOKEN') //make sure a right token is selected
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean install'
            }
        }

        stage('Test and jacoco') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                    jacoco(
                        execPattern: 'target/jacoco.exec',
                        classPattern: 'target/classes',
                        sourcePattern: '.'
                        )
                        archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                    }
                }
        }
// Check the SonarQube analysis stage for the correct project key and name
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv("${env.SONARQUBE_SERVER}") {
                    sh """
                        sonar-scanner ^
                        -Dsonar.projectKey=BMI_CL ^
                        -Dsonar.sources=src ^
                        -Dsonar.projectName=BMI_CL ^
                        -Dsonar.host.url=http://localhost:9000 ^
                        -Dsonar.login=${env.SONAR_TOKEN} ^
                        -Dsonar.java.binaries=target/classes
                    """
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh 'docker build -t "$DOCKER_IMAGE:latest" .'
                }
            }
        }

        stage('Push Docker Image to Docker Hub') {
            steps {
                withCredentials([
                    usernamePassword(credentialsId: 'dockerhub',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD')
                    }
                }
            }
        }
    }


}
