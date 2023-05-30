#on macOS, if activemq and postgres are installed via homebrew, start their services
if [[ "$OSTYPE" == "darwin"* ]]; then
	activemq start
	pg_ctl -D /usr/local/var/postgres start
fi
./agent/gradlew -b ./agent/build.gradle -PmainClass=nl.bliss.gui.BLISS runApp --args="-file external/GUI.json"
