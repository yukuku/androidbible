Bible for Android
=================

**100% Free, Open-Source, Quick and Friendly Bible App.**

- <a href="http://androidbible.blogspot.com">Development Blog</a>
- <a href="https://plus.google.com/109017775855333478187">Google+</a>
- <a href="http://groups.google.com/group/androidbible">Discussion list</a>
- <a href="https://play.google.com/store/apps/details?id=yuku.alkitab">Official app with Indonesian as default, "Alkitab"</a>
- <a href="https://play.google.com/store/apps/details?id=yuku.alkitab.kjv">Official app with English KJV as default, "Quick Bible"</a>

Bible translations/versions
---------------------------

This app natively uses *.yes* files for the Bible text. You can create a *.yes* file easily by preparing a plain text file. <a href="http://goo.gl/QEw0j">See instructions</a>.

You can also convert PalmBible+ PDB files using the built-in converter in the app or use the <a href="http://pdb2yes.alkitab-host.appspot.com/">pdb2yes online converter</a> 
that produces compressed YES files.

Integration with other apps
---------------------------

If you build an app that refers to the Bible, you can let the user read the Bible either in this app or in your app.
An Android library project `AlkitabIntegration` is available that you can include in your project. Your app can easily 
open this Bible app at a specified verse or get verses from this Bible app and display them in your app.

If you need help integrating it, please contact me.

Building
--------

You'll need `http://code.google.com/p/yuku-android-util/` cloned to a directory in the same level
as this repo's cloned directory. So you will have `yuku-android-util` and `androidbible` side-by-side. 
The main project is `Alkitab` inside `androidbible`. By the way, Alkitab is the Indonesian word for Bible.

This repository does not contain Bible text.

License
--------

    Copyright 2009-2013 The Alkitab Authors.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

