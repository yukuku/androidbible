Bible for Android
=================

**Really Quick Bible App. 100% Free, No-Ads, Quick and Friendly.**

- Blog: http://androidbible.blogspot.com
- Google+: https://plus.google.com/109017775855333478187
- Mailing list: http://groups.google.com/group/androidbible
- Official app with Indonesian as default, "Alkitab": https://play.google.com/store/apps/details?id=yuku.alkitab
- Official app with English KJV as default, "Quick Bible": https://play.google.com/store/apps/details?id=yuku.alkitab.kjv

Integration with other apps
---------------------------

If you build an app that refer to the Bible, you can let the user read the Bible either in this app or in your app.
There is a Android library project `AlkitabIntegration` that you can include in your project, so your app can easily 
open this Bible app at a specified verse location or get verses from this Bible app and display them in your app.

If you need help integrating it, you can contact me.

Bible translations/versions
---------------------------

This app natively uses *.yes* files for the Bible text. You can create a *.yes* file easily by creating a plain text file
called a *.yet* file and converting it using the Yet2Yes utility. You will need just Java, you don't need Eclipse or Android SDK.
Here are the instructions: http://goo.gl/QEw0j

Building
--------

You'll need `http://code.google.com/p/yuku-android-util/` cloned to a directory in the same level
as this repo's cloned directory. So you will have `yuku-android-util` and `androidbible` side-by-side. 
The main project is `Alkitab` inside `androidbible`. BTW, Alkitab is Indonesian word for Bible.

This repository does not contain any Bible text.
