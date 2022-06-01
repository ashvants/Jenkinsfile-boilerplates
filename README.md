# Jenkinsfile-boilerplates
Jenkinsfile for windows dot net application build, version and pushing the package to artifactory.
The powershell file for amending the version numbers is kept seperately. 
Later amendments i would like to push the version back in to the code repository.
Please do raise an issue if you need this file as well.
Also i will be soon pushing seperate jenkins file for codescan requirements. Though you can integrate the code scan requirements in to the main jenkinsfile., based on the requirments of daily scheduled scan i would prefer to keep this as seperate pipeline becuase this would create unnecessary builds and packages pushed on to artifactory.  
