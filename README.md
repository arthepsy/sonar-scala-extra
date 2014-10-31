Scala plugins for SonarQube
===========================
[![build status](https://travis-ci.org/arthepsy/sonar-scala-extra.svg)](https://travis-ci.org/arthepsy/sonar-scala-extra/)

Requirements
------------
* SonarQube 4.5+  
* [Scala plugin](https://github.com/arthepsy/sonar-scala)


Scapegoat plugin
----------------
Creates rule repository and issues from [Scapegoat](https://github.com/sksamuel/scalac-scapegoat-plugin) report.  

### Configuration  

Set the location of generated **`scapegoat.xml`** report with `sonar.scala.scapegoat.reportPath` configuration parameter, e.g:

    sonar.scala.scapegoat.reportPath=target/scapegoat-report/scapegoat.xml

