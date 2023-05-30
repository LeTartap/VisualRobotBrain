# Welcome to the BLISS-DM page

## Requirements
- JDK 8+ (https://www.oracle.com/technetwork/java/javase/downloads/index.html)
- Microphone and speakers/headphones
- Apache ActiveMQ Classic (https://activemq.apache.org/)
- PostgreSQL 9+ (https://www.postgresql.org/about/)
- API Keys/servers for the ASR and TTS
- Gradle 6+ (https://gradle.org/)
- Python >= 3.5 < 3.7 (3.7* is possible with manual fix)
- (Ubuntu): canberra-gtk-module

## Installing and setting up
First install the JDK to be able to run the components of the prototype. Install Gradle for being able to build the project and run `gradle wrapper` in the agent folder. Then install ActiveMQ, which serves as a middleware component between the ASR, TTS and the agent. (https://activemq.apache.org/getting-started.html)

Install a PostgreSQL server and have it running locally. You should set a database name, a password, IP and role. In the source code of the agent, there is a file called blissflipper.properties. Here you can adjust the database settings. By default Flipper expects the settings below, but you can change it to host it remotely (which is a bit more complex to set up):
* host = 127.0.0.1
* database = cb
* role = cb
* password = coffeebot

**[You don't need the Python script, though the dialogue is not adaptive then anymore.]** The last step would to set up a virtual Python environment (Python = 3.6), and installing the required packages. The `requirements.txt` is located in the external folder. There's a ReadMe that includes more details on the installation.

## Configuration
<!--The system uses configuration files, which will be updated in the near future, but for now the most important ones are located in `src/resources/external`, where you need to modify `ASR.json` and `TTS.json` with the appropriate keys (and change their name from `ASR_global` to `ASR` and `TTS_global` to `TTS` respectively). The `GUI.json` file is the configuration needed for the GUI, where it will display the dialogue.-->
Configuration now works more conveniently in the GUI, in which you can select your Flipper templates, ASR and TTS configuration of your liking. Configuration for each service needs to be located in `src/resources/external` and need to have a name that starts with `ASR' and `TTS' respectively for ASR and TTS and need to be in `.json` format. For Flipper templates it needs to be a `.properties' file. By default BLISS supports the following services:
### ASR
* [Spraak UT](https://github.com/opensource-spraakherkenning-nl/Kaldi_NL) (English and Dutch)
* [Spraak RU](https://github.com/opensource-spraakherkenning-nl/Kaldi_NL) (Only Dutch)
* [Google Speech](https://cloud.google.com/speech-to-text/docs/quickstart-client-libraries) (Any language Google Speech supports)

### TTS
* [ReadSpeaker](https://www.readspeaker.com/nl/solutions/tekst-naar-spraak-voor-online-lezen/readspeaker-speechcloud-api/) (Any language applicable for your API)
* [MaryTTS](https://github.com/marytts/marytts/wiki/Local-MaryTTS-Server-Installation) (No Dutch support)
* [Google Speech](https://cloud.google.com/text-to-speech/docs/quickstart-client-libraries) (Any language Google Speech supports) 

### Flipper templates
* DRONGO configuration [blissflipper.properties](https://gitlab.com/bliss-nl/bliss-dm/-/blob/master/agent/src/main/resources/data/dm/blissflipper.properties)
* BLISS V2 configuration [blissv2.properties](https://gitlab.com/bliss-nl/bliss-dm/-/blob/master/agent/src/main/resources/data/dm/blissv2.properties)
* Coffeebot configuration [cbflipper.properties](https://gitlab.com/bliss-nl/bliss-dm/-/blob/master/agent/src/main/resources/data/dm/cbflipper.properties)

## Running
Checks before running the agent:
 * Go the external folder. To run the simple NLP server, launch the python script in the newly created environment with the command `python python_server.py.` To see if it's working, you can visit http://127.0.0.1:8090 and should see the RestAPI.
 * Make sure ActiveMQ is running
 * Make sure there is a database service running

In the main folder, you can use the batch or shell scripts for launching the system. You can use the `start.bat` on Windows or `start.sh` on Unix.

You can stop all processes by exiting the GUI. Within the GUI you can do the following things:
 * Configure the ASR, TTS and Flipper.
 * Start and stop the ASR
 * Start and stop the TTS
 * Start and stop the DM
 * Record the audio of the user (and agent, though still in beta)
 * Reset the dialogue (this will update the timestamp)
 * Set a unique ID, in the format of timestamp_id_gender_age_region (this is the same for the database and the recording)
 * Send user text messages instead of using ASR
 * Sent agent text messages instead of using TTS

 
## Dialogue
In the `resources\behaviour` folder of the agent there files for detecting user intents and generating agent intents.

## Logs
By default the system saves logs as well, containing the transcriptions. Though you can save conversations through the `File` menu as well.

## Maintainer
Jelte van Waterschoot (j.b.vanwaterschoot@utwente.nl)
