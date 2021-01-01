<img src="/readme/logo.png" width="300"/>

# The Sonosynthesiser - Android App

An android app, written in Kotlin, to provide an interesting accessibility portal to the [BBC SFX Archive](https://sound-effects.bbcrewind.co.uk/).

## Requirements

The app requires the following permissions:
- Full network access.
- Read and write access to external storage.

It is also required to have [the-sonosynthesiser-api](https://github.com/eddireeder/the-sonosynthesiser-api) running for the app to retrieve which sounds are selected and begin downloading. 

## Features

The app features:
- Automatically deletes old sounds and downloads new ones based on the sounds selected in [the-sonosynthesiser-api](https://github.com/eddireeder/the-sonosynthesiser-api).
- Sounds are spread evenly in all directions, to be explored and listened to my moving the device.
- Sounds become louder as the user gets closer, and quieter as the user moves away.
- Once close enough, the sound will begin to *tune* and information (from the BBC archive) will appear about the sound effect.
- The UI consists of a swarm of particles that compact as the user moves closer to a sound, and spread out if the user is far from any sound.

## Screenshots

<img src="/readme/title-screenshot.png" width="200"/> <img src="/readme/untuned-screenshot.png" width="200"/> <img src="/readme/tuned-screenshot.png" width="200"/>

## Adding Sounds

There are 16000+ sounds in the BBC SFX archive. [the-sonosynthesiser-admin-app](https://github.com/eddireeder/the-sonosynthesiser-admin-app) should be used as an interface for selecting which of the sounds should be downloaded onto the device (along with some other parameters).

[the-sonosynthesiser-admin-app](https://github.com/eddireeder/the-sonosynthesiser-admin-app) makes requests to:
- The BBC SFX archive for retrieving all the sounds.
- [the-sonosynthesiser-api](https://github.com/eddireeder/the-sonosynthesiser-api) to change the selected sounds.

## 
