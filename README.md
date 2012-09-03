Bible for Android
=================

**Really Quick Bible App. 100% Free, No-Ads, Straight-Forward and Friendly.**

Some public information about the project
-----------------------------------------

- Blog: http://androidbible.blogspot.com
- Google+: https://plus.google.com/109017775855333478187
- Mailing list: http://groups.google.com/group/androidbible
- Official app with Indonesian as default, **Alkitab**: https://play.google.com/store/apps/details?id=yuku.alkitab
- Official app with English KJV as default, **Quick Bible**: https://play.google.com/store/apps/details?id=yuku.alkitab.kjv

This document describes how to build Bible for Android from source codes to .apk. 

Requirements
------------

- Eclipse (at least the Java IDE)
- ADT (Android Development Tools) plugin for eclipse, at least version 14
- Android SDKs. 

Bible text sources
------------------

This repository does not contain any Bible text, partially because they are sometimes copyrighted. 
Follow the instructions below (soon) to incorporate Bible text before building the app.

Step-by-step tutorial on building
---------------------------------

Getting the sources

1. Clone the Bible for Android git repository to <your directory of choice>/androidbible

    `git clone git@github.com:yukuku/androidbible.git`
    
   You may need to use public/private key pairs to easily authenticate yourself. 

2. Clone the yuku-android-util project from Google Code to <your directory of choice>/yuku-android-util/

    `git clone https://yukuku@code.google.com/p/yuku-android-util/`
    
   See the full instructions here: http://code.google.com/p/yuku-android-util/source/checkout

Setting up

1. Open Eclipse and handle the mess by yourself.

2. Install ADT to Eclipse, handle the problems by yourself.

3. Restart Eclipse as necessary

4. (Optional) Go to Eclipse Preferences -> Android -> Build
   use the debug.keystore on the alkitab-android repository as the "Custom debug keystore". 

5. Import the following projects into Eclipse:
   - from this repository:
     - Alkitab
     - AlkitabConverter (Optional)
     - AlkitabYes
     - BiblePlus
     - KpriViewerLib
     - KpriModel
   - from yuku-android-util:
     - Afw
     - ActionBarSherlock4
     - AmbilWarna
     - KirimFidbek
     - BintexReader
     - FileChooser
     - AndroidCrypto
     - FlowLayout
     - SdkSearchBar
     - SimpleTrove
     - NewQuickAction3DLib
   - ... and you know that this document might be outdated, so you need to add necessary projects by yourself.

Building and running

1. Right click on Alkitab project on Package Explorer and select Run -> Android Application.

Troubleshooting

- If there are errors in the project, just clean all projects (menu Project -> Clean). Usually it works.

