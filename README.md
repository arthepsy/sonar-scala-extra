Scala plugins for SonarQube
===========================
[![build status](https://travis-ci.org/arthepsy/sonar-scala-extra.svg)](https://travis-ci.org/arthepsy/sonar-scala-extra/)
[![Coverage Status](https://coveralls.io/repos/arthepsy/sonar-scala-extra/badge.svg?branch=master)](https://coveralls.io/r/arthepsy/sonar-scala-extra?branch=master)  

Requirements
------------
* SonarQube 4.5+  
* [Scala plugin](https://github.com/arthepsy/sonar-scala)


Scapegoat plugin
----------------
Imports [Scapegoat](https://github.com/sksamuel/scalac-scapegoat-plugin) inspections as rule repository and reflects as issues from generated report.  

### Configuration  

Set the location of generated **`scapegoat.xml`** report with `sonar.scala.scapegoat.reportPath` configuration parameter, e.g:

    sonar.scala.scapegoat.reportPath=target/scapegoat-report/scapegoat.xml

