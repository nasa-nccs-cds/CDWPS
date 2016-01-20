###                                BDWPS Project

_WPS implementation built on scala/play, designed to interface to big data frameworks such as Spark, Akka, etc. Implements the ESGF-CWT climate data services api._

####  Prerequisite: Install the Scala develpment tools:

    1) Scala:                     http://www.scala-lang.org/download/install.html                   
                        
    
    2) Scala Build Tool (sbt):    http://www.scala-sbt.org/0.13/docs/Setup.html
                        

####  Install and run BDWPS:

    1) Checkout the BDWPS sources:

        >> git clone https://github.com/nasa-nccs-cds/BDWPS.git

    2) Build and run the application:

        >> cd BDWPS
        >> sbt run

     3) Access demos:

        In a browser open the page "http://localhost:9000/wps/demo"


####  Code development:

    1) Install IntelliJ IDEA CE from https://www.jetbrains.com/idea/download/ with Scala plugin enabled.
    
    2) Start IDEA and import the BDWPS Project from Version Control (github) using the address https://github.com/nasa-nccs-cds/BDWPS.git.
    

####  Project Configuration:

    1) Logging: Edit conf/logback.xml
    

    

