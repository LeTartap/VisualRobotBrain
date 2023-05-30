rem  This batch file starts the GUI

@echo off

   ./agent/gradlew -b ./agent/build.gradle -PmainClass=nl.bliss.gui.BLISS run --args="-file external/GUI.json"
      
@echo on

