pipeline {
    agent any

    environment {
        // Android SDK 配置
        ANDROID_HOME = '/opt/android-sdk'
        PATH = "${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/34.0.0:${env.PATH}"

        // 蒾公英配置
        PGYER_API_KEY = credentials('pgyer-api-key')

        // 签名配置 - 使用 Secret File 凭据
        KEYSTORE_FILE = credentials('nasmovie-keystore')
        STORE_PASSWORD = credentials('nasmovie-store-password')
        KEY_ALIAS = 'nasmovie'
        KEY_PASSWORD = credentials('nasmovie-key-password')
    }

    stages {
        stage('拉取代码') {
            steps {
                echo '[1/6] 开始拉取代码...'
                git branch: 'master', url: 'https://github.com/zhaoyz6910/NasMovie.git'
                echo '[1/6] 代码拉取完成'
            }
        }

        stage('配置签名') {
            steps {
                echo '[2/6] 配置签名文件...'
                script {
                    // 创建 keystore.properties 文件
                    writeFile file: 'keystore.properties', text: """
storePassword=${STORE_PASSWORD}
keyAlias=${KEY_ALIAS}
keyPassword=${KEY_PASSWORD}
"""
                    // KEYSTORE_FILE 是 Jenkins 凭据提供的临时文件路径
                    // 复制到项目目录，命名为项目特定的名称
                    sh 'cp ${KEYSTORE_FILE} ./nasmovie-key.jks'
                }
                echo '[2/6] 签名配置完成'
            }
        }

        stage('清理项目') {
            steps {
                echo '[3/6] 清理项目...'
                sh 'chmod +x gradlew'
                sh './gradlew clean'
                echo '[3/6] 清理完成'
            }
        }

        stage('构建 Debug APK') {
            steps {
                echo '[4/6] 构建 Debug APK...'
                sh './gradlew assembleDebug'
                echo '[4/6] Debug APK 构建完成'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'app/build/outputs/apk/debug/*.apk', fingerprint: true
                }
            }
        }

        stage('构建 Release APK') {
            steps {
                echo '[5/6] 构建 Release APK...'
                sh './gradlew assembleRelease'
                echo '[5/6] Release APK 构建完成'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'app/build/outputs/apk/release/*.apk', fingerprint: true
                }
            }
        }

        stage('上传到蒲公英') {
            steps {
                echo '[6/6] 上传 APK 到蒲公英...'
                script {
                    def debugApk = sh(returnStdout: true, script: 'find app/build/outputs/apk/debug -name "*.apk" | head -1').trim()
                    def releaseApk = sh(returnStdout: true, script: 'find app/build/outputs/apk/release -name "*.apk" | head -1').trim()

                    if (debugApk && !debugApk.isEmpty()) {
                        echo '上传 Debug APK...'
                        sh """
                            curl -F "file=@${debugApk}" \
                            -F "_api_key=${PGYER_API_KEY}" \
                            https://www.pgyer.com/apiv2/app/upload
                        """
                        echo 'Debug APK 已上传到蒲公英'
                    }

                    if (releaseApk && !releaseApk.isEmpty()) {
                        echo '上传 Release APK...'
                        sh """
                            curl -F "file=@${releaseApk}" \
                            -F "_api_key=${PGYER_API_KEY}" \
                            https://www.pgyer.com/apiv2/app/upload
                        """
                        echo 'Release APK 已上传到蒲公英'
                    }
                }
                echo '[6/6] 上传完成'
            }
        }
    }

    post {
        success {
            echo '✅ 构建成功！'
            echo 'APK 已上传到蒲公英，可在蒲公英后台查看下载链接'
        }
        failure {
            echo '❌ 构建失败，请检查日志'
        }
    }
}