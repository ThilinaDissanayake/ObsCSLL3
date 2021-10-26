# ObsCSLL3
This is the device list for the CSLL3 android software, specially designed for obstacle detection using acoustic signals.
In these device list, you can find programs that can be used to record sensor data or programs that can be used to program onboard devices as probing devices on android smart devices, including wearables, using CSLL3 software. 
For the purpose of sensing obstacles using acoustic waves, we use the following sensor modelities.
1. android microphone:
  This uses the microphone/microphones of the smartphone to record audio in mono/stereo in .wav format. As .wav does not support stereo recording, the data will be saved as a mono file, which we will separate into stereo tracks during the pre-processing. There are options to choose snap recording or continuous recording.
2. sweep: generates sine sweep signals at a given frequency range. The inaudible ranges in android devices, given the speaker specifications, lies between 18 kHz - 21 kHz.
