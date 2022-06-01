import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.EnvVars
import groovy.io.*

pipeline {
    triggers {
     pollSCM('* * * * *')
    }


    agent any
    environment {
        PROJECT_PATH = "${env.WORKSPACE}"
        BUILD_NUMBER = "${env.BUILD_NUMBER}"
        BUILD_FILE = "${env.WORKSPACE}\\build\\RR.msbuild"
        CMD_PACKAGE_FOLDER = "${env.WORKSPACE}\\your_dotnet_application.Components\\Package"

        BUILD_APP_PS_SCRIPT = '$env:WORKSPACE\\your_dotnet_application.Components\\Deployment\\BuildApplication.ps1'
        ASSEMBLY_FILE = '$env:WORKSPACE\\your_dotnet_application.Components\\1stlocation.your_dotnet_application\\properties\\assemblyInfo.cs'
        ASSEMBLY_FILE1 = '$env:WORKSPACE\\your_dotnet_application.Components\\2ndlocation\\properties\\assemblyinfo.cs'
        ENVIRONMENT_FILE = '$env:WORKSPACE\\your_dotnet_application.Components\\Deployment\\Env-BuildFeatureFlag.csv'
        MS_BUILD = "C:\\Program Files (x86)\\MSBuild\\14.0\\Bin\\msbuild"

        PS_BUILD = '$env:WORKSPACE\\Deployment\\BuildApplication.ps1'
        RELEASE_FOLDER = '$env:WORKSPACE\\your_dotnet_application.Components\\your_dotnet_application\\bin\\Release\\'
        PACKAGE_FOLDER = '$env:WORKSPACE\\your_dotnet_application.Components\\Package'
        PUBLISHED_FOLDER = '$env:WORKSPACE\\your_dotnet_application.Components\\Publish\\'
        DEPLOYMENT_FOLDER = '$env:WORKSPACE\\your_dotnet_application.Components\\Deployment\\'

        ARTIFACTORY_API_KEY = credentials('artifactoryapikey')
        ARTIFACTORY_URL = 'https://artifactory.co.au/artifactory'
        ARTIFACTORY_REPO = 'application-local'
        PACKAGE_NAME = 'your_dotnet_application'
	
    }

    options { disableConcurrentBuilds() }
    
 
    
    stages {
        stage('Clear dir') {
            steps {
                deleteDir()
            }
        }
// Essentially the checkout does not need to be done, Jenkins does this automatically 
        stage('Checkout') {
            steps {
                    checkout scm                               
            }
        }

        stage('Add Build Number') {
            steps {
                powershell returnStatus: true, script: """
                                                            . $BUILD_APP_PS_SCRIPT; AddJenkinsBuildNum -AddBuildNum $BUILD_NUMBER -AssemblyFilePath $ASSEMBLY_FILE
                                                            . $BUILD_APP_PS_SCRIPT; AddJenkinsBuildNum -AddBuildNum $BUILD_NUMBER -AssemblyFilePath $ASSEMBLY_FILE1
                                                        """
            }
        }
 
             
       stage('Build') {
            steps {
                        bat 'call "%MS_BUILD%" "%BUILD_FILE%" /t:Package /p:Configuration=release'
            }
        }


        stage('Package') {
                steps {
                        script {
                    String VERSION = powershell(script:". $BUILD_APP_PS_SCRIPT; GetVersion -AssemblyFilePath $ASSEMBLY_FILE", returnStdout: true)
                    String PACKAGE_VERSION = VERSION.replaceAll('[\n\r]', '')
                    env.PACKAGE_VER  = VERSION.replaceAll('[\n\r]', '')
                  //  env.GIT_BRANCH = getGitBranchName().replaceAll('[\n\r]', '')
                  //  env.GIT_BRANCH_URL = scm.userRemoteConfigs[0].url 
                    powershell(script:"""  
                                        . $BUILD_APP_PS_SCRIPT; CreateDeployments \
                                            -Name $PACKAGE_NAME \
                                            -PublisherName "YourName $PACKAGE_NAME" \
                                            -SrcDir $RELEASE_FOLDER \
                                            -OutDir $PUBLISHED_FOLDER \
                                            -ToolsPath $DEPLOYMENT_FOLDER \
                                            -DestinationFolderPath $PACKAGE_FOLDER \
                                            -PackageName $PACKAGE_NAME \
                                            -Version $PACKAGE_VERSION
                                """, returnStatus: true)
                    
                    powershell returnStatus: true, script: """
                                    if (Test-Path '$JENKINS_COMMON\\$PACKAGE_NAME') {
                                            rm -r -fo '$JENKINS_COMMON\\$PACKAGE_NAME'
                                    }
                                    mkdir '$JENKINS_COMMON\\$PACKAGE_NAME'    
                                    New-Object -TypeName PSCustomObject -Property @{
                                        MainBranch = '${env.JOB_BASE_NAME}'
                                        ProjectName = '$PACKAGE_NAME'
                                        BuildVersion = '$PACKAGE_VER'
                                    } | Export-Csv -Path '$JENKINS_COMMON\\your_dotnet_application\\your_dotnet_application.csv' -NoTypeInformation -Append
                                    """
                            
                        }
                        
                }
        }
    
      
        stage('Push') {
            when { branch "master" }
                steps {
                    script {
                            def list = []
                            def workspace = env.WORKSPACE
                            def dir = new File("${env.WORKSPACE}\\your_dotnet_application.Components\\Package")
                            fetFilenamesFromDir(dir,list)

                            for (FilePath in list) {
                                                    String FileNames = powershell(script:" (get-item -Path $FilePath).name ", returnStdout: true)
                                                    String FileName = FileNames.replaceAll("[\n\r]", "");
                                                    String[] FNames = FileName.split('_');
                                                    String FName = FNames[0]
                                                    String FName_n = FNames[0].replaceAll(" ", "");
                                                    String Env_n = FNames[1]
                                                    String Version_n = FNames[2].replaceAll(".zip", "");
                                                        
                                                    //Artifactory Env Folder Structured 
                                                    env.TARGET_URL_STRUCTURED = "$ARTIFACTORY_URL/$ARTIFACTORY_REPO/$OLD_PACKAGE_NAME/$Env_n/$Version_n/$FileName"
                                                    //Artifactory Env Folder Structured 
                                                    env.TARGET_FILE_NAME = FileNames.replaceAll("[\n\r]", "");
                                                    env.TARGET_URL_UNSTRUCTURED = "$ARTIFACTORY_URL/$ARTIFACTORY_REPO/$OLD_PACKAGE_NAME/$Env_n-$Version_n/$FileName"
                                                    //Post Package to Artifactory Repository        
                                                    bat (script: ' curl -X PUT -H "X-JFrog-Art-Api: %ARTIFACTORY_API_KEY%" -T "%CMD_PACKAGE_FOLDER%\\%TARGET_FILE_NAME%" "%TARGET_URL_STRUCTURED%"')
                            }
                    }
                }
        }

        stage('E-Mail')  {
		    steps
	                    {
                            emailext (
                            to: "myself@myself.com",
                            from: "Jenkins@myself.com",
                            subject: "JENKINS BUILD - ${PACKAGE_NAME} :  ${env.PACKAGE_VER} - ${currentBuild.currentResult}",
                            body: """<p><h3><u> JENKINS BUILD DETAIL - ${PACKAGE_NAME}_${env.PACKAGE_VER} </u></h3><br>
                                    <b>BUILD STATUS: </b> <a href='${env.BUILD_URL}'>${currentBuild.currentResult}</a> <br><br>
                                    <b>DURATION: </b> ${currentBuild.durationString} <br><br>
                                   
                                    <b>JOB: </b> <a href='${env.JOB_DISPLAY_URL}'>${env.JOB_BASE_NAME} [${env.BUILD_NUMBER}]</a> <br><br>
                                    <b>JENKINS ARTIFACTS: </b> <a href='${env.RUN_ARTIFACTS_DISPLAY_URL}'>${env.RUN_ARTIFACTS_DISPLAY_URL}</a> <br><br>
                                    <b>ARTIFACTORY URL: </b> <a href='${env.ARTIFACTORY_URL}/${ARTIFACTORY_REPO}/${OLD_PACKAGE_NAME}'>${env.ARTIFACTORY_URL}/${ARTIFACTORY_REPO}/${OLD_PACKAGE_NAME}</a> <br>
                     <br>Check console output at <a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a></p>""",
                            )
		                // <b>GIT BRANCH: </b> <a href='${env.GIT_BRANCH_URL}'>${env.GIT_BRANCH}</a> <br><br>

	                    }
            }
        
    }
  post {
       always {
                archiveArtifacts artifacts: 'your_dotnet_application.Components\\Package\\**', fingerprint: true, caseSensitive: false
                
                
        }
    }
}
def PowerShell(psCmd) {
    psCmd = psCmd.replaceAll('%', '%%')
    bat "powershell.exe -NonInteractive -ExecutionPolicy Bypass -Command \"\$ErrorActionPreference='Stop';[Console]::OutputEncoding=[System.Text.Encoding]::UTF8;$psCmd;EXIT \$global:LastExitCode\""
}
/*
def getGitBranchName() {
    return scm.branches[0].name
}
*/
@NonCPS
def fetFilenamesFromDir(def dir, def list) {
    dir.eachFileRecurse (FileType.FILES) { file ->
        file = file.toString()
        if (file.endsWith('zip')) {
            list << file
        }
    }
}

 
