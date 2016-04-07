###                                BDWPS Project

_WPS implementation built on scala/play, designed to interface to big data frameworks such as Spark, Akka, etc. Includes a plugin interface to implement various service APIs.  Distributed with a plugin (https://github.com/nasa-nccs-cds/esgfWpsApi) implementing the ESGF-CWT climate data services api._

####  Prerequisite: Install the Scala develpment tools:

    1) Scala:                     http://www.scala-lang.org/download/install.html                   
                        
    
    2) Scala Build Tool (sbt):    http://www.scala-sbt.org/0.13/docs/Setup.html
                        

####  Install and run BDWPS:

    0) Install dependent projects (this step will become unnecessary when the NASA Maven server is up and running):
    
        >> git clone https://github.com/nasa-nccs-cds/CDAPI.git
        >> cd CDAPI; sbt publish-local
        >> git clone https://github.com/nasa-nccs-cds/KernelModuleTemplate.git
        >> cd KernelModuleTemplate; sbt publish-local
        >> git clone https://github.com/nasa-nccs-cds/CDS2.git
        >> cd CDS2; sbt publish-local
        
    1) Optional Dependencies
    
        Executing async requests requires that the NetCDF C library be installed on the server.  The best ways to accopmplish this are:
        
            a) Use a package manager, see: http://www.unidata.ucar.edu/software/netcdf/docs/getting_and_building_netcdf.html
            b) Install UVCDAT, see: https://github.com/UV-CDAT/uvcdat/wiki/install.  In this case one will need to execute the UVCDAT 'setup_runtime.sh' script before starting the server.

    2) Checkout the BDWPS sources:

        >> git clone https://github.com/nasa-nccs-cds/BDWPS.git

    3) Build and run the application:

        >> cd BDWPS
        >> sbt run

     4) Access demos:

        In a browser open the page "http://localhost:9000/wps/demo"


####  Code development:

    1) Install IntelliJ IDEA CE from https://www.jetbrains.com/idea/download/ with Scala plugin enabled.
    
    2) Start IDEA and import the BDWPS Project from Version Control (github) using the address https://github.com/nasa-nccs-cds/BDWPS.git.
    
    3) Service provider/api plugin development: See https://github.com/nasa-nccs-cds/esgfWpsApi

    

####  Project Configuration:

    1) Logging: See conf/logback.xml
    
    2) Configure plugins: See app/servers/Configuration.scala
    
    

    

