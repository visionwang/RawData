# Installation instructions

Environment: Window operating system  & JDK 1.7

Note: To facilitate testing, please keep the absolute paths consistent with those in instructions document.

Step 1: Unzip the plugin-decca.zip to the directory: D:\plugin-decca
Recommended directory structure:

D:\plugin-decca

├

├─decca-1.0.jar : decca source code

├─decca-1.0.pom : decca’s pom 

├─soot-1.0.jar : program static analysis tool

├

├─ apache-maven-3.2.5: maven source code

Step 2: Install the plugin
Execute the following cmd command to install soot:
D:\plugin-decca\apache-maven-3.2.5\bin\mvn.bat install:install-file  -Dfile=D:\plugin-decca\soot-1.0.jar  -DgroupId=neu.lab  -DartifactId=soot -Dversion=1.0 -Dpackaging=jar


Execute the following cmd command to install decca:
D:\plugin-decca\apache-maven-3.2.5\bin\mvn.bat install:install-file  -Dfile=D:\plugin-decca\decca-1.0.jar  -DgroupId=neu.lab  -DartifactId=decca -Dversion=1.0 -Dpackaging=maven-plugin -DpomFile=D:\plugin-decca\decca-1.0.pom



# How to use Decca?

Dataset: 1. Ground truth dataset.xlsx (Ground truth dataset discription)
         
         2. gitHub_old (all the subjects with DC issues for testing)

Execute Decca:
Execute the following cmd command:
D:\plugin-decca\apache-maven-3.2.5\bin\mvn.bat -f=D:\rawData\gitHub_old\abdera-abdera-1.1.2-RC2\extensions\features package -Dmaven.test.skip=true neu.lab:decca:1.0:detect

# The results of Decca

<project project="Project name" projectInfo=" Absolute path of the project">
  <conflicts>
    <conflictJar groupId-artifactId="Conflicting JAR name" versions="Version No." riskLevel="Warning level"">
      <versions>
        <version versionId="Unloaded conflicting version" loaded="false">
          <path>Dependency path from host project to unloaded JAR</path>
        </version>
      </versions>
      <RiskMethods tip="method that may be used but will not be loaded !">
        <RiskMthd>the referenced feature set but will not be loaded</RiskMthd>
      </RiskMethods>
    </conflictJar>
  </conflicts>
</project>


