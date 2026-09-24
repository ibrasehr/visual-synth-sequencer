Visual Synth Sequencer



Interactive 16-Step Audio Sequencer \& Visualizer

Visual Synth Sequencer is a real-time, interactive audio sequencer built in Java using Swing and javax.sound.sampled. The application provides a dynamic matrix interface that allows users to compose sound patterns, manipulate waveforms, and visualize playback live.



Core Capabilities



16x8 Interactive Grid: Toggle active beat nodes dynamically with custom visual UI states.



Programmatic Audio Synthesis: On-the-fly sine and square waveform generation without external audio assets.



Real-Time Playhead Animation: Visual sweeping indicator synchronized with audio playback and adjustable BPM.



Pattern Persistence: Save and load custom composition patterns to disk.



Team Roles \& Folder Ownership



Person 1 | Audio Systems Engineer | src/audio/ | feature/audio

Person 2 | UI \& Visuals Engineer | src/ui/ | feature/ui

Person 3 | Data \& Persistence Engineer | src/model/ | feature/data

Person 4 | App Lead \& Integration | src/app/ | feature/app



Ownership Rule

One owner per module folder. If your work requires touching someone else's folder, coordinate with them directly before making changes.



Branch Strategy \& PR Rules



Default branch: main



No direct commits to main. All work happens on feature/\* branches.



Pull Requests (PRs) are reviewed and merged into main at integration checkpoints.



Feature Branches:



feature/audio — Person 1



feature/ui — Person 2



feature/data — Person 3



feature/app — Person 4



Getting Started



Prerequisites



Java Development Kit (JDK) 11+



Git



Clone \& Setup



git clone https://github.com/YOUR-USERNAME/visual-synth-sequencer.git

cd visual-synth-sequencer



Checkout your feature branch:

git checkout feature/



Compilation \& Execution



Compile all source packages:

javac -d bin src/audio/.java src/model/.java src/ui/.java src/app/.java



Run the application:

java -cp bin app.MainApp



Project Structure



visual-synth-sequencer/

├── src/

│   ├── audio/       # Person 1 — tone generation \& audio thread handling

│   ├── ui/          # Person 2 — Graphics2D matrix UI \& animations

│   ├── model/       # Person 3 — grid state model \& pattern file I/O

│   └── app/         # Person 4 — main window, control loop, \& integration

├── .gitignore

└── README.md

